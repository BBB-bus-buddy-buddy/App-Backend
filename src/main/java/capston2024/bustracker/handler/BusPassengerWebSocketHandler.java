package capston2024.bustracker.handler;

import capston2024.bustracker.config.ConnectionLimitInterceptor;
import capston2024.bustracker.config.dto.BusBoardingDTO;
import capston2024.bustracker.config.dto.BusRealTimeStatusDTO;
import capston2024.bustracker.config.dto.PassengerLocationDTO;
import capston2024.bustracker.service.BusService;
import capston2024.bustracker.service.PassengerLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 개선된 승객 앱과의 WebSocket 통신 핸들러
 * - 메모리 누수 방지
 * - 배터리 최적화 고려
 * - 에러 처리 강화
 * - 자동 탑승/하차 감지 개선
 * - 완전한 로깅 추가
 */
@Component
@Slf4j
public class BusPassengerWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    // ==============================
    // 🗂️ 세션 관리 맵들
    // ==============================

    // 조직별 승객 세션 관리
    private final Map<String, Set<WebSocketSession>> organizationSessions = new ConcurrentHashMap<>();
    // 세션 역매핑을 위한 맵
    private final Map<String, String> sessionToOrgMap = new ConcurrentHashMap<>();
    // 세션과 사용자 ID 매핑
    private final Map<String, String> sessionToUserMap = new ConcurrentHashMap<>();
    // 마지막 활동 시간 추적
    private final Map<String, Instant> lastActivityMap = new ConcurrentHashMap<>();

    // 세션 정리를 위한 스케줄러
    private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(1);

    // ==============================
    // 🏗️ 생성자 및 초기화
    // ==============================

    @Autowired
    public BusPassengerWebSocketHandler(ObjectMapper objectMapper, ApplicationContext applicationContext) {
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;

        log.info("🏗️ [Handler초기화] BusPassengerWebSocketHandler 생성자 호출");

        // 10분마다 비활성 세션 정리
        log.info("⏲️ [Handler초기화] 세션 정리 스케줄러 설정 (10분 간격)");
        cleanupScheduler.scheduleAtFixedRate(this::cleanupInactiveSessions, 10, 10, TimeUnit.MINUTES);

        log.info("✅ [Handler초기화] BusPassengerWebSocketHandler 초기화 완료");
    }

    // ==============================
    // 🌱 지연 초기화 서비스 빈들
    // ==============================

    // 지연 초기화를 통해 BusService 얻기 (순환 의존성 방지)
    private BusService getBusService() {
        try {
            BusService busService = applicationContext.getBean(BusService.class);
            log.debug("🔧 [서비스빈] BusService 빈 조회 성공");
            return busService;
        } catch (Exception e) {
            log.error("❌ [서비스빈] BusService 빈 조회 실패: {}", e.getMessage());
            throw new RuntimeException("BusService를 찾을 수 없습니다", e);
        }
    }

    // 지연 초기화를 통해 PassengerLocationService 얻기
    private PassengerLocationService getPassengerLocationService() {
        try {
            PassengerLocationService locationService = applicationContext.getBean(PassengerLocationService.class);
            log.debug("🔧 [서비스빈] PassengerLocationService 빈 조회 성공");
            return locationService;
        } catch (Exception e) {
            log.error("❌ [서비스빈] PassengerLocationService 빈 조회 실패: {}", e.getMessage());
            throw new RuntimeException("PassengerLocationService를 찾을 수 없습니다", e);
        }
    }

    // ==============================
    // 🔗 WebSocket 연결 관리
    // ==============================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");

        log.info("🟢 [연결설정] 승객 WebSocket 연결 설정: 세션 ID = {}, IP = {}", sessionId, clientIp);

        // 활동 시간 초기화
        lastActivityMap.put(sessionId, Instant.now());
        log.debug("⏰ [연결설정] 세션 활동 시간 초기화: {}", sessionId);

        // 연결 성공 메시지 전송
        try {
            Map<String, Object> welcomeMessage = Map.of(
                    "type", "connection_established",
                    "status", "success",
                    "message", "웹소켓 연결이 성공적으로 설정되었습니다.",
                    "instructions", "조직 ID와 함께 subscribe 메시지를 보내주세요.",
                    "timestamp", System.currentTimeMillis()
            );

            log.info("📤 [연결설정] 환영 메시지 전송 시도: {}", sessionId);
            sendMessage(session, welcomeMessage);
            log.info("✅ [연결설정] 환영 메시지 전송 완료: {}", sessionId);

        } catch (Exception e) {
            log.error("❌ [연결설정] 연결 확인 메시지 전송 실패: 세션={}, 오류={}", sessionId, e.getMessage(), e);
        }

        log.info("🎯 [연결설정] 승객 WebSocket 연결 설정 완료: {}", sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String organizationId = sessionToOrgMap.remove(sessionId);
        String userId = sessionToUserMap.remove(sessionId);
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");

        log.info("🔴 [연결종료] 승객 WebSocket 연결 종료 시작: 세션 ID = {}, 상태 = {}", sessionId, status.getCode());

        if (organizationId != null) {
            log.info("🧹 [연결종료] 조직별 세션에서 제거: 조직={}, 세션={}", organizationId, sessionId);

            Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
            if (sessions != null) {
                boolean removed = sessions.remove(session);
                log.debug("📋 [연결종료] 세션 제거 결과: {}, 남은 세션 수: {}", removed, sessions.size());

                // 조직에 세션이 없으면 맵에서 제거
                if (sessions.isEmpty()) {
                    organizationSessions.remove(organizationId);
                    log.info("🗑️ [연결종료] 조직의 모든 세션 제거됨: {}", organizationId);
                }
            }

            log.info("✅ [연결종료] 승객 WebSocket 연결 종료: 세션 ID = {}, 조직 ID = {}, 사용자 ID = {}, 상태 = {}",
                    sessionId, organizationId, userId != null ? userId.substring(0, Math.min(10, userId.length())) + "***" : null, status.getCode());
        } else {
            log.info("⚠️ [연결종료] 조직 ID 없는 세션 종료: 세션 ID = {}, 상태 = {}", sessionId, status.getCode());
        }

        // 활동 시간 정보 제거
        lastActivityMap.remove(sessionId);
        log.debug("🧹 [연결종료] 활동 시간 정보 제거: {}", sessionId);

        // IP별 연결 수 감소
        if (clientIp != null) {
            ConnectionLimitInterceptor.decrementConnection(clientIp);
            log.debug("📉 [연결종료] IP별 연결 수 감소: {}", clientIp);
        }

        log.info("🎯 [연결종료] 승객 WebSocket 연결 종료 처리 완료: {}", sessionId);
    }

    // ==============================
    // 📨 메시지 처리
    // ==============================

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String sessionId = session.getId();

        log.info("📥 [메시지수신] 승객 메시지 수신: 세션={}, 크기={}bytes", sessionId, payload.length());
        log.debug("📋 [메시지수신] 메시지 내용: {}", payload);

        // 활동 시간 업데이트
        lastActivityMap.put(sessionId, Instant.now());
        log.debug("⏰ [메시지수신] 세션 활동 시간 업데이트: {}", sessionId);

        try {
            // JSON 파싱
            log.debug("🔧 [메시지처리] JSON 파싱 시작: {}", sessionId);
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            String messageType = (String) data.get("type");
            String organizationId = (String) data.get("organizationId");

            log.info("🔍 [메시지처리] 메시지 파싱 완료: 타입={}, 조직={}, 세션={}", messageType, organizationId, sessionId);

            // 기본 검증
            if (messageType == null) {
                log.warn("❌ [메시지처리] 메시지 타입 누락: {}", sessionId);
                sendErrorMessage(session, "메시지 타입이 필요합니다.");
                return;
            }

            // 조직 ID가 필요한 메시지 타입들
            if (needsOrganizationId(messageType) && (organizationId == null || organizationId.isEmpty())) {
                log.warn("❌ [메시지처리] 조직 ID 누락: 타입={}, 세션={}", messageType, sessionId);
                sendErrorMessage(session, "조직 ID가 필요합니다.");
                return;
            }

            // 세션 맵핑 등록 (처음 메시지를 보낼 때)
            if (organizationId != null && !sessionToOrgMap.containsKey(sessionId)) {
                log.info("📝 [메시지처리] 새 세션 등록: 조직={}, 세션={}", organizationId, sessionId);
                registerSession(session, organizationId);
            }

            // 메시지 타입에 따른 처리
            log.info("🎯 [메시지처리] 메시지 타입별 처리 시작: {}", messageType);

            switch (messageType) {
                case "subscribe":
                    log.info("📢 [메시지처리] 구독 메시지 처리: {}", sessionId);
                    handleSubscribeMessage(session, data);
                    break;
                case "boarding":
                    log.info("🚌 [메시지처리] 탑승/하차 메시지 처리: {}", sessionId);
                    handleBoardingMessage(session, data);
                    break;
                case "location":
                    log.info("📍 [메시지처리] 위치 메시지 처리: {}", sessionId);
                    handleLocationMessage(session, data);
                    break;
                case "heartbeat":
                    log.debug("💓 [메시지처리] 하트비트 메시지 처리: {}", sessionId);
                    handleHeartbeat(session);
                    break;
                case "get_bus_status":
                    log.info("🚍 [메시지처리] 버스 상태 조회 메시지 처리: {}", sessionId);
                    handleGetBusStatus(session, data);
                    break;
                default:
                    log.warn("❓ [메시지처리] 알 수 없는 메시지 타입: {}, 세션: {}", messageType, sessionId);
                    sendErrorMessage(session, "알 수 없는 메시지 타입: " + messageType);
            }

            log.info("✅ [메시지처리] 메시지 처리 완료: 타입={}, 세션={}", messageType, sessionId);

        } catch (Exception e) {
            log.error("❌ [메시지처리] 승객 메시지 처리 중 오류 발생: 세션={}, 페이로드크기={}, 오류={}",
                    sessionId, payload.length(), e.getMessage(), e);
            sendErrorMessage(session, "메시지 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // ==============================
    // 🔍 메시지 검증 및 세션 관리
    // ==============================

    private boolean needsOrganizationId(String messageType) {
        boolean needs = !"heartbeat".equals(messageType);
        log.debug("🔍 [검증] 조직 ID 필요 여부: 타입={}, 필요={}", messageType, needs);
        return needs;
    }

    private void registerSession(WebSocketSession session, String organizationId) {
        String sessionId = session.getId();

        log.info("📝 [세션등록] 승객 세션 등록 시작: 조직={}, 세션={}", organizationId, sessionId);

        // 세션-조직 매핑 저장
        sessionToOrgMap.put(sessionId, organizationId);

        // 조직별 세션 집합에 추가
        Set<WebSocketSession> sessions = organizationSessions.computeIfAbsent(organizationId, k -> {
            log.debug("🆕 [세션등록] 새 조직 세션 집합 생성: {}", organizationId);
            return ConcurrentHashMap.newKeySet();
        });

        boolean added = sessions.add(session);

        log.info("✅ [세션등록] 승객 세션 등록 완료: 조직={}, 세션={}, 추가됨={}, 총세션수={}",
                organizationId, sessionId, added, sessions.size());
    }

    // ==============================
    // 📍 위치 메시지 처리 (핵심 기능)
    // ==============================

    /**
     * 승객 위치 메시지 처리 - 배터리 최적화 고려
     */
    private void handleLocationMessage(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();

        log.info("📍 [위치메시지] 위치 메시지 처리 시작: 세션={}", sessionId);

        try {
            // 위치 데이터 추출
            Map<String, Object> locationData = (Map<String, Object>) data.get("data");

            if (locationData == null) {
                log.warn("❌ [위치메시지] 위치 데이터 누락: 세션={}", sessionId);
                sendErrorMessage(session, "위치 데이터가 필요합니다.");
                return;
            }

            log.debug("🗺️ [위치메시지] 위치 데이터 추출 성공: 세션={}, 데이터키={}", sessionId, locationData.keySet());

            // 데이터 추출 및 검증
            String userId = (String) locationData.get("userId");
            Double latitude = getDoubleValue(locationData.get("latitude"));
            Double longitude = getDoubleValue(locationData.get("longitude"));
            Object timestampObj = locationData.get("timestamp");

            log.info("🔍 [위치메시지] 위치 정보 추출: 사용자={}, 위도={}, 경도={}, 세션={}",
                    userId != null ? userId.substring(0, Math.min(10, userId.length())) + "***" : null,
                    latitude, longitude, sessionId);

            // 필수 필드 검증
            if (userId == null || latitude == null || longitude == null) {
                log.warn("❌ [위치메시지] 필수 필드 누락: 사용자={}, 위도={}, 경도={}, 세션={}",
                        userId != null, latitude != null, longitude != null, sessionId);
                sendErrorMessage(session, "사용자 ID, 위도, 경도가 필요합니다.");
                return;
            }

            // GPS 좌표 유효성 검증
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                log.warn("❌ [위치메시지] 유효하지 않은 GPS 좌표: 위도={}, 경도={}, 세션={}",
                        latitude, longitude, sessionId);
                sendErrorMessage(session, "유효하지 않은 GPS 좌표입니다.");
                return;
            }

            // 한국 좌표계 범위 추가 체크
            if (latitude < 33.0 || latitude > 39.0 || longitude < 124.0 || longitude > 132.0) {
                log.warn("⚠️ [위치메시지] 한국 외부 좌표: 위도={}, 경도={}, 세션={}",
                        latitude, longitude, sessionId);
                // 경고만 하고 처리는 계속
            }

            // DTO 생성
            PassengerLocationDTO locationDTO = new PassengerLocationDTO();
            locationDTO.setUserId(userId);
            locationDTO.setOrganizationId((String) data.get("organizationId"));
            locationDTO.setLatitude(latitude);
            locationDTO.setLongitude(longitude);
            locationDTO.setTimestamp(timestampObj != null ?
                    getLongValue(timestampObj) : System.currentTimeMillis());

            log.info("🚀 [위치메시지] PassengerLocationDTO 생성 완료: 사용자={}, 조직={}, 세션={}",
                    userId.substring(0, Math.min(10, userId.length())) + "***",
                    locationDTO.getOrganizationId(), sessionId);

            // 사용자 ID 저장
            sessionToUserMap.put(sessionId, userId);
            log.debug("📝 [위치메시지] 세션-사용자 매핑 저장: 세션={}", sessionId);

            // 위치 처리 서비스 호출 (배터리 최적화 포함)
            log.info("🎯 [위치메시지] PassengerLocationService 호출: 세션={}", sessionId);
            boolean boardingDetected = getPassengerLocationService().processPassengerLocation(locationDTO);

            log.info("📊 [위치메시지] 위치 처리 완료: 탑승감지={}, 사용자={}, 세션={}",
                    boardingDetected, userId.substring(0, Math.min(10, userId.length())) + "***", sessionId);

            // 결과에 따른 응답
            if (boardingDetected) {
                log.info("🎉 [위치메시지] 자동 탑승/하차 감지됨! 사용자={}, 세션={}",
                        userId.substring(0, Math.min(10, userId.length())) + "***", sessionId);

                // 자동 탑승 감지 시 클라이언트에 알림
                sendSuccessMessage(session, "버스 탑승/하차가 자동으로 감지되었습니다.");

                // 추가 알림 메시지
                Map<String, Object> boardingNotification = Map.of(
                        "type", "boarding_detected",
                        "userId", userId,
                        "timestamp", System.currentTimeMillis(),
                        "message", "자동 탑승/하차 감지"
                );
                sendMessage(session, boardingNotification);

            } else {
                log.debug("📍 [위치메시지] 위치 처리됨 (탑승 감지 없음): 사용자={}, 세션={}",
                        userId.substring(0, Math.min(10, userId.length())) + "***", sessionId);

                // 일반적인 위치 업데이트 확인
                Map<String, Object> locationResponse = Map.of(
                        "type", "location_processed",
                        "status", "success",
                        "userId", userId,
                        "timestamp", System.currentTimeMillis()
                );
                sendMessage(session, locationResponse);
            }

            log.info("✅ [위치메시지] 위치 메시지 처리 완료: 세션={}", sessionId);

        } catch (Exception e) {
            log.error("❌ [위치메시지] 위치 메시지 처리 중 오류 발생: 세션={}, 오류={}", sessionId, e.getMessage(), e);
            sendErrorMessage(session, "위치 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // ==============================
    // 🛠️ 유틸리티 메서드들
    // ==============================

    private Double getDoubleValue(Object value) {
        if (value == null) {
            log.debug("🔢 [변환] Double 변환: null 값");
            return null;
        }

        try {
            if (value instanceof Number) {
                Double result = ((Number) value).doubleValue();
                log.debug("🔢 [변환] Double 변환 성공 (Number): {} -> {}", value, result);
                return result;
            } else if (value instanceof String) {
                Double result = Double.parseDouble((String) value);
                log.debug("🔢 [변환] Double 변환 성공 (String): {} -> {}", value, result);
                return result;
            } else {
                log.warn("⚠️ [변환] 지원하지 않는 타입: {}", value.getClass().getSimpleName());
                return Double.parseDouble(value.toString());
            }
        } catch (NumberFormatException e) {
            log.error("❌ [변환] Double 변환 실패: value={}, 오류={}", value, e.getMessage());
            return null;
        }
    }

    private Long getLongValue(Object value) {
        if (value == null) {
            log.debug("🔢 [변환] Long 변환: null 값");
            return null;
        }

        try {
            if (value instanceof Number) {
                Long result = ((Number) value).longValue();
                log.debug("🔢 [변환] Long 변환 성공 (Number): {} -> {}", value, result);
                return result;
            } else if (value instanceof String) {
                Long result = Long.parseLong((String) value);
                log.debug("🔢 [변환] Long 변환 성공 (String): {} -> {}", value, result);
                return result;
            } else {
                return Long.parseLong(value.toString());
            }
        } catch (NumberFormatException e) {
            log.error("❌ [변환] Long 변환 실패: value={}, 오류={}", value, e.getMessage());
            return null;
        }
    }

    // ==============================
    // ⚠️ 오류 처리
    // ==============================

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = session.getId();
        String userId = sessionToUserMap.get(sessionId);
        String organizationId = sessionToOrgMap.get(sessionId);

        log.error("⚠️ [통신오류] 승객 WebSocket 통신 오류: 세션={}, 사용자={}, 조직={}, 오류={}",
                sessionId,
                userId != null ? userId.substring(0, Math.min(10, userId.length())) + "***" : null,
                organizationId, exception.getMessage());

        log.error("🔍 [통신오류] 상세 스택 트레이스:", exception);

        // 오류 발생 시 세션 정리
        try {
            log.info("🧹 [통신오류] 오류 발생 세션 강제 종료 시작: {}", sessionId);
            session.close();
            log.info("✅ [통신오류] 세션 강제 종료 완료: {}", sessionId);
        } catch (Exception e) {
            log.error("❌ [통신오류] 세션 강제 종료 실패: 세션={}, 오류={}", sessionId, e.getMessage());
        }
    }

    // ==============================
    // 📢 브로드캐스트 기능
    // ==============================

    /**
     * 특정 조직의 모든 승객에게 버스 상태 업데이트 전송
     * - 네트워크 효율성을 위한 배치 전송
     */
    public void broadcastBusStatus(String organizationId, BusRealTimeStatusDTO busStatus) {
        log.info("📢 [브로드캐스트] 버스 상태 브로드캐스트 시작: 조직={}", organizationId);

        Set<WebSocketSession> sessions = organizationSessions.get(organizationId);

        if (sessions == null || sessions.isEmpty()) {
            log.warn("⚠️ [브로드캐스트] 대상 세션 없음: 조직={}", organizationId);
            return;
        }

        log.info("🎯 [브로드캐스트] 대상 세션 수: {}, 조직={}", sessions.size(), organizationId);

        Map<String, Object> message = Map.of(
                "type", "busUpdate",
                "data", busStatus,
                "timestamp", System.currentTimeMillis()
        );

        log.debug("📤 [브로드캐스트] 브로드캐스트 메시지 생성: 타입={}", message.get("type"));

        // 성공/실패 카운터
        final int[] successCount = {0};
        final int[] failCount = {0};

        // 병렬 처리로 성능 향상
        sessions.parallelStream().forEach(session -> {
            if (session != null && session.isOpen()) {
                try {
                    sendMessage(session, message);
                    successCount[0]++;
                    log.debug("✅ [브로드캐스트] 개별 전송 성공: 세션={}", session.getId());
                } catch (Exception e) {
                    failCount[0]++;
                    log.error("❌ [브로드캐스트] 개별 전송 실패: 세션={}, 오류={}", session.getId(), e.getMessage());

                    // 전송 실패한 세션은 정리 대상으로 표시
                    markSessionForCleanup(session);
                }
            } else {
                failCount[0]++;
                log.warn("⚠️ [브로드캐스트] 비활성 세션 발견: 세션={}", session != null ? session.getId() : "null");
                if (session != null) {
                    markSessionForCleanup(session);
                }
            }
        });

        log.info("📊 [브로드캐스트] 브로드캐스트 완료: 조직={}, 성공={}, 실패={}, 총세션={}",
                organizationId, successCount[0], failCount[0], sessions.size());
    }

    // ==============================
    // 📊 통계 및 모니터링
    // ==============================

    /**
     * 조직별 활성 승객 수 조회
     */
    public int getActivePassengerCount(String organizationId) {
        Set<WebSocketSession> sessions = organizationSessions.get(organizationId);

        if (sessions == null) {
            log.debug("📊 [통계] 조직별 활성 승객 수: 조직={}, 세션=null, 활성승객=0", organizationId);
            return 0;
        }

        // 유효한 세션만 카운트
        int activeCount = (int) sessions.stream()
                .filter(session -> session != null && session.isOpen())
                .count();

        log.debug("📊 [통계] 조직별 활성 승객 수: 조직={}, 총세션={}, 활성승객={}",
                organizationId, sessions.size(), activeCount);

        return activeCount;
    }

    /**
     * 전체 활성 승객 수 조회
     */
    public int getTotalActivePassengerCount() {
        int totalActive = organizationSessions.values().stream()
                .mapToInt(sessions -> (int) sessions.stream()
                        .filter(session -> session != null && session.isOpen())
                        .count())
                .sum();

        int totalSessions = organizationSessions.values().stream()
                .mapToInt(Set::size)
                .sum();

        log.debug("📊 [통계] 전체 활성 승객 수: 총세션={}, 활성승객={}", totalSessions, totalActive);

        return totalActive;
    }

    // ==============================
    // 📝 메시지 타입별 처리기들
    // ==============================

    /**
     * 구독 메시지 처리 - 초기 데이터 제공
     */
    private void handleSubscribeMessage(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();
        String organizationId = (String) data.get("organizationId");

        log.info("📢 [구독처리] 구독 메시지 처리 시작: 조직={}, 세션={}", organizationId, sessionId);

        try {
            log.info("🚌 [구독처리] 해당 조직의 모든 버스 상태 조회 시작: {}", organizationId);

            // 해당 조직의 모든 버스 상태 즉시 전송
            getBusService().getAllBusStatusByOrganizationId(organizationId).forEach(busStatus -> {
                try {
                    Map<String, Object> message = Map.of(
                            "type", "busUpdate",
                            "data", busStatus,
                            "timestamp", System.currentTimeMillis()
                    );

                    log.debug("📤 [구독처리] 개별 버스 상태 전송: 버스={}, 세션={}",
                            busStatus.getBusNumber(), sessionId);

                    sendMessage(session, message);
                } catch (Exception e) {
                    log.error("❌ [구독처리] 초기 버스 상태 전송 중 오류 발생: 세션={}, 오류={}",
                            sessionId, e.getMessage());
                }
            });

            log.info("✅ [구독처리] 모든 버스 상태 전송 완료: 조직={}, 세션={}", organizationId, sessionId);
            sendSuccessMessage(session, "구독이 성공적으로 등록되었습니다.");

        } catch (Exception e) {
            log.error("❌ [구독처리] 구독 처리 중 오류 발생: 조직={}, 세션={}, 오류={}",
                    organizationId, sessionId, e.getMessage(), e);
            sendErrorMessage(session, "구독 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 승객 탑승/하차 메시지 처리
     */
    private void handleBoardingMessage(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();

        log.info("🚌 [탑승처리] 탑승/하차 메시지 처리 시작: 세션={}", sessionId);

        try {
            Map<String, Object> boardingData = (Map<String, Object>) data.get("data");

            if (boardingData == null) {
                log.warn("❌ [탑승처리] 탑승/하차 데이터 누락: 세션={}", sessionId);
                sendErrorMessage(session, "탑승/하차 데이터가 필요합니다.");
                return;
            }

            String busNumber = (String) boardingData.get("busNumber");
            String userId = (String) boardingData.get("userId");
            String actionStr = (String) boardingData.get("action");

            log.info("🔍 [탑승처리] 탑승/하차 정보 추출: 버스={}, 사용자={}, 액션={}, 세션={}",
                    busNumber,
                    userId != null ? userId.substring(0, Math.min(10, userId.length())) + "***" : null,
                    actionStr, sessionId);

            if (busNumber == null || userId == null || actionStr == null) {
                log.warn("❌ [탑승처리] 필수 필드 누락: 버스={}, 사용자={}, 액션={}, 세션={}",
                        busNumber != null, userId != null, actionStr != null, sessionId);
                sendErrorMessage(session, "버스 번호, 사용자 ID, 액션이 필요합니다.");
                return;
            }

            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber(busNumber);
            boardingDTO.setOrganizationId((String) data.get("organizationId"));
            boardingDTO.setUserId(userId);

            try {
                boardingDTO.setAction(BusBoardingDTO.BoardingAction.valueOf(actionStr.toUpperCase()));
                log.debug("✅ [탑승처리] 액션 파싱 성공: {}", boardingDTO.getAction());
            } catch (IllegalArgumentException e) {
                log.warn("❌ [탑승처리] 유효하지 않은 액션: {}, 세션={}", actionStr, sessionId);
                sendErrorMessage(session, "유효하지 않은 액션입니다. BOARD 또는 ALIGHT를 사용하세요.");
                return;
            }

            boardingDTO.setTimestamp(System.currentTimeMillis());

            log.info("🚀 [탑승처리] BusService.processBusBoarding 호출: 버스={}, 액션={}, 세션={}",
                    busNumber, boardingDTO.getAction(), sessionId);

            boolean success = getBusService().processBusBoarding(boardingDTO);

            log.info("📊 [탑승처리] 탑승/하차 처리 결과: 성공={}, 버스={}, 액션={}, 세션={}",
                    success, busNumber, boardingDTO.getAction(), sessionId);

            if (success) {
                String message = boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD ?
                        "탑승이 성공적으로 처리되었습니다." : "하차가 성공적으로 처리되었습니다.";

                log.info("✅ [탑승처리] 탑승/하차 성공: {}", message);
                sendSuccessMessage(session, message);
            } else {
                log.warn("❌ [탑승처리] 탑승/하차 실패: 버스={}, 액션={}", busNumber, boardingDTO.getAction());
                sendErrorMessage(session, "탑승/하차 처리에 실패했습니다. 버스 상태를 확인해주세요.");
            }

        } catch (Exception e) {
            log.error("❌ [탑승처리] 탑승/하차 메시지 처리 중 오류 발생: 세션={}, 오류={}", sessionId, e.getMessage(), e);
            sendErrorMessage(session, "탑승/하차 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 하트비트 처리
     */
    private void handleHeartbeat(WebSocketSession session) {
        String sessionId = session.getId();

        log.debug("💓 [하트비트] 하트비트 처리: 세션={}", sessionId);

        try {
            Map<String, Object> heartbeatResponse = Map.of(
                    "type", "heartbeat_response",
                    "status", "alive",
                    "timestamp", System.currentTimeMillis()
            );

            sendMessage(session, heartbeatResponse);
            log.debug("✅ [하트비트] 하트비트 응답 전송 완료: 세션={}", sessionId);
        } catch (Exception e) {
            log.error("❌ [하트비트] 하트비트 응답 전송 실패: 세션={}, 오류={}", sessionId, e.getMessage());
        }
    }

    /**
     * 버스 상태 조회 처리
     */
    private void handleGetBusStatus(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();
        String organizationId = (String) data.get("organizationId");
        String busNumber = (String) data.get("busNumber");

        log.info("🚍 [버스상태] 버스 상태 조회 처리: 조직={}, 버스={}, 세션={}", organizationId, busNumber, sessionId);

        try {
            if (busNumber != null) {
                log.info("🔍 [버스상태] 특정 버스 상태 조회 요청: 버스={}", busNumber);
                // 특정 버스 상태 조회
                // 구현 필요: BusService에서 특정 버스 상태 조회 메서드 추가
                log.warn("⚠️ [버스상태] 특정 버스 상태 조회 미구현");
                sendErrorMessage(session, "특정 버스 상태 조회는 아직 구현되지 않았습니다.");
            } else {
                log.info("📋 [버스상태] 전체 버스 상태 조회 - 구독 처리로 위임");
                // 전체 버스 상태 조회
                handleSubscribeMessage(session, data);
            }
        } catch (Exception e) {
            log.error("❌ [버스상태] 버스 상태 조회 중 오류 발생: 조직={}, 버스={}, 세션={}, 오류={}",
                    organizationId, busNumber, sessionId, e.getMessage(), e);
            sendErrorMessage(session, "버스 상태 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // ==============================
    // 📤 메시지 전송 유틸리티들
    // ==============================

    /**
     * 성공 메시지 전송
     */
    private void sendSuccessMessage(WebSocketSession session, String message) {
        String sessionId = session.getId();

        try {
            Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", message,
                    "timestamp", System.currentTimeMillis()
            );

            log.debug("📤 [메시지전송] 성공 메시지 전송: 세션={}, 메시지={}", sessionId, message);
            sendMessage(session, response);
            log.debug("✅ [메시지전송] 성공 메시지 전송 완료: 세션={}", sessionId);

        } catch (Exception e) {
            log.error("❌ [메시지전송] 성공 메시지 전송 중 오류 발생: 세션={}, 오류={}", sessionId, e.getMessage());
        }
    }

    /**
     * 오류 메시지 전송
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        String sessionId = session.getId();

        log.warn("⚠️ [메시지전송] 에러 메시지 전송: 세션={}, 메시지={}", sessionId, errorMessage);

        try {
            Map<String, Object> response = Map.of(
                    "status", "error",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );

            sendMessage(session, response);
            log.debug("✅ [메시지전송] 에러 메시지 전송 완료: 세션={}", sessionId);

        } catch (Exception e) {
            log.error("❌ [메시지전송] 오류 메시지 전송 중 오류 발생: 세션={}, 원본오류={}, 전송오류={}",
                    sessionId, errorMessage, e.getMessage());
        }
    }

    /**
     * 세션에 메시지 전송 (내부 헬퍼 메서드)
     */
    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (session == null) {
            log.warn("⚠️ [메시지전송] null 세션으로 메시지 전송 시도");
            return;
        }

        String sessionId = session.getId();

        if (!session.isOpen()) {
            log.warn("⚠️ [메시지전송] 닫힌 세션으로 메시지 전송 시도: 세션={}", sessionId);
            return;
        }

        try {
            String jsonMessage = objectMapper.writeValueAsString(message);

            log.debug("📡 [메시지전송] JSON 직렬화 완료: 세션={}, 크기={}bytes", sessionId, jsonMessage.length());

            synchronized (session) { // 동시성 문제 방지
                session.sendMessage(new TextMessage(jsonMessage));
            }

            log.debug("✅ [메시지전송] 메시지 전송 성공: 세션={}", sessionId);

        } catch (IOException e) {
            log.error("❌ [메시지전송] 메시지 전송 실패: 세션={}, 오류={}", sessionId, e.getMessage());
            throw e;
        }
    }

    // ==============================
    // 🧹 세션 정리 및 관리
    // ==============================

    /**
     * 비활성 세션들 정리 (10분 이상 비활성)
     */
    private void cleanupInactiveSessions() {
        Instant threshold = Instant.now().minusSeconds(600); // 10분 임계값

        log.info("🧹 [세션정리] 비활성 세션 정리 시작: 임계값={}", threshold);

        int removedCount = 0;
        int totalSessions = lastActivityMap.size();

        // 활동 시간 기준 정리
        removedCount += lastActivityMap.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            Instant lastActivity = entry.getValue();

            if (lastActivity.isBefore(threshold)) {
                log.info("🗑️ [세션정리] 비활성 세션 발견: 세션={}, 마지막활동={}",
                        sessionId, lastActivity);

                // 세션 정리
                String organizationId = sessionToOrgMap.remove(sessionId);
                String userId = sessionToUserMap.remove(sessionId);

                if (organizationId != null) {
                    Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
                    if (sessions != null) {
                        sessions.removeIf(session -> session.getId().equals(sessionId));
                        log.debug("📋 [세션정리] 조직별 세션에서 제거: 조직={}, 세션={}", organizationId, sessionId);

                        if (sessions.isEmpty()) {
                            organizationSessions.remove(organizationId);
                            log.info("🗑️ [세션정리] 빈 조직 세션 집합 제거: 조직={}", organizationId);
                        }
                    }
                }

                log.info("✅ [세션정리] 비활성 세션 정리 완료: 세션={}, 조직={}, 사용자={}",
                        sessionId, organizationId,
                        userId != null ? userId.substring(0, Math.min(10, userId.length())) + "***" : null);

                return true; // 맵에서 제거
            }

            return false;
        });

        log.info("📊 [세션정리] 비활성 세션 정리 완료: 전체={}, 제거={}, 남은={}",
                totalSessions, removedCount, lastActivityMap.size());
    }

    /**
     * 문제 있는 세션을 정리 대상으로 표시
     */
    private void markSessionForCleanup(WebSocketSession session) {
        if (session != null) {
            String sessionId = session.getId();

            log.info("🏷️ [세션정리] 문제 세션을 정리 대상으로 표시: 세션={}", sessionId);

            // 즉시 정리하지 않고 다음 정리 주기에서 처리되도록 활동 시간을 오래 전으로 설정
            lastActivityMap.put(sessionId, Instant.now().minusSeconds(700));

            log.debug("✅ [세션정리] 세션 정리 마킹 완료: 세션={}", sessionId);
        }
    }

    // ==============================
    // 🛑 셧다운 및 리소스 정리
    // ==============================

    /**
     * 셧다운 훅 - 애플리케이션 종료 시 리소스 정리
     */
    @PreDestroy
    public void shutdown() {
        log.info("🛑 [셧다운] BusPassengerWebSocketHandler 종료 시작...");

        // 모든 세션 정리
        log.info("🧹 [셧다운] 모든 WebSocket 세션 정리 시작");

        int totalSessions = 0;
        int closedSessions = 0;

        for (Set<WebSocketSession> sessions : organizationSessions.values()) {
            for (WebSocketSession session : sessions) {
                totalSessions++;
                try {
                    if (session.isOpen()) {
                        session.close(CloseStatus.GOING_AWAY);
                        closedSessions++;
                        log.debug("✅ [셧다운] 세션 종료: {}", session.getId());
                    }
                } catch (Exception e) {
                    log.error("❌ [셧다운] 세션 종료 중 오류: 세션={}, 오류={}", session.getId(), e.getMessage());
                }
            }
        }

        log.info("📊 [셧다운] 세션 정리 완료: 전체={}, 종료={}", totalSessions, closedSessions);

        // 스케줄러 종료
        log.info("⏲️ [셧다운] 스케줄러 종료 시작");

        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("⚠️ [셧다운] 스케줄러 정상 종료 시간 초과 - 강제 종료");
                cleanupScheduler.shutdownNow();
            } else {
                log.info("✅ [셧다운] 스케줄러 정상 종료 완료");
            }
        } catch (InterruptedException e) {
            log.warn("⚠️ [셧다운] 스케줄러 종료 중 인터럽트 발생 - 강제 종료");
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 맵들 정리
        organizationSessions.clear();
        sessionToOrgMap.clear();
        sessionToUserMap.clear();
        lastActivityMap.clear();

        log.info("🧹 [셧다운] 모든 맵 정리 완료");
        log.info("✅ [셧다운] BusPassengerWebSocketHandler 종료 완료");
    }
}