package capston2024.bustracker.handler;

import capston2024.bustracker.config.ConnectionLimitInterceptor;
import capston2024.bustracker.config.dto.BusBoardingDTO;
import capston2024.bustracker.config.dto.BusRealTimeStatusDTO;
import capston2024.bustracker.config.dto.BusSeatDTO;
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
 */
@Component
@Slf4j
public class BusPassengerWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

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

    @Autowired
    public BusPassengerWebSocketHandler(ObjectMapper objectMapper, ApplicationContext applicationContext) {
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;

        log.info("🏗️ [승객WebSocket] BusPassengerWebSocketHandler 초기화");

        // 10분마다 비활성 세션 정리
        cleanupScheduler.scheduleAtFixedRate(this::cleanupInactiveSessions, 10, 10, TimeUnit.MINUTES);

        log.info("⏲️ [승객WebSocket] 세션 정리 스케줄러 시작 (10분 간격)");
    }

    // 지연 초기화를 통해 BusService 얻기 (순환 의존성 방지)
    private BusService getBusService() {
        return applicationContext.getBean(BusService.class);
    }

    // 지연 초기화를 통해 PassengerLocationService 얻기
    private PassengerLocationService getPassengerLocationService() {
        return applicationContext.getBean(PassengerLocationService.class);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");
        log.info("🟢 [승객WebSocket] 연결 설정: 세션 ID = {}, IP = {}", session.getId(), clientIp);

        // 활동 시간 초기화
        lastActivityMap.put(session.getId(), Instant.now());
        log.debug("⏰ [승객WebSocket] 활동 시간 초기화: 세션 ID = {}", session.getId());

        // 연결 성공 메시지 전송
        try {
            sendMessage(session, Map.of(
                    "type", "connection_established",
                    "status", "success",
                    "message", "웹소켓 연결이 성공적으로 설정되었습니다.",
                    "instructions", "조직 ID와 함께 subscribe 메시지를 보내주세요.",
                    "timestamp", System.currentTimeMillis()
            ));
            log.info("✅ [승객WebSocket] 연결 확인 메시지 전송 완료: 세션 ID = {}", session.getId());
        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 연결 확인 메시지 전송 실패: 세션 ID = {}, 오류 = {}",
                    session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String organizationId = sessionToOrgMap.remove(sessionId);
        String userId = sessionToUserMap.remove(sessionId);
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");

        log.info("🔴 [승객WebSocket] 연결 종료 시작: 세션 ID = {}, 조직 ID = {}, 사용자 ID = {}, 상태 = {}",
                sessionId, organizationId, userId, status.getCode());

        if (organizationId != null) {
            Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
            if (sessions != null) {
                sessions.remove(session);
                log.debug("🧹 [승객WebSocket] 조직 세션에서 제거: 조직 ID = {}, 남은 세션 수 = {}",
                        organizationId, sessions.size());

                // 조직에 세션이 없으면 맵에서 제거
                if (sessions.isEmpty()) {
                    organizationSessions.remove(organizationId);
                    log.info("🗑️ [승객WebSocket] 조직 세션 맵 제거: 조직 ID = {}", organizationId);
                }
            }
        }

        // 활동 시간 정보 제거
        lastActivityMap.remove(sessionId);
        log.debug("⏰ [승객WebSocket] 활동 시간 정보 제거: 세션 ID = {}", sessionId);

        // IP별 연결 수 감소
        if (clientIp != null) {
            ConnectionLimitInterceptor.decrementConnection(clientIp);
            log.debug("🔢 [승객WebSocket] IP 연결 수 감소: IP = {}", clientIp);
        }

        log.info("✅ [승객WebSocket] 연결 종료 완료: 세션 ID = {}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String sessionId = session.getId();

        log.info("📥 [승객WebSocket] 메시지 수신: 세션 ID = {}, 크기 = {}bytes, 내용 = {}",
                sessionId, payload.length(), payload);

        // 활동 시간 업데이트
        lastActivityMap.put(sessionId, Instant.now());
        log.debug("⏰ [승객WebSocket] 활동 시간 업데이트: 세션 ID = {}", sessionId);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String messageType = (String) data.get("type");
            String organizationId = (String) data.get("organizationId");

            log.info("🔧 [승객WebSocket] 메시지 파싱 완료: 타입 = {}, 조직 ID = {}, 세션 ID = {}",
                    messageType, organizationId, sessionId);

            // 기본 검증
            if (messageType == null) {
                log.warn("⚠️ [승객WebSocket] 메시지 타입 누락: 세션 ID = {}", sessionId);
                sendErrorMessage(session, "메시지 타입이 필요합니다.");
                return;
            }

            // 조직 ID가 필요한 메시지 타입들
            if (needsOrganizationId(messageType) && (organizationId == null || organizationId.isEmpty())) {
                log.warn("⚠️ [승객WebSocket] 조직 ID 누락: 메시지 타입 = {}, 세션 ID = {}",
                        messageType, sessionId);
                sendErrorMessage(session, "조직 ID가 필요합니다.");
                return;
            }

            // 세션 맵핑 등록 (처음 메시지를 보낼 때)
            if (organizationId != null && !sessionToOrgMap.containsKey(sessionId)) {
                log.info("📝 [승객WebSocket] 세션 등록 시도: 조직 ID = {}, 세션 ID = {}",
                        organizationId, sessionId);
                registerSession(session, organizationId);
            }

            // 메시지 타입에 따른 처리
            log.info("🔄 [승객WebSocket] 메시지 타입별 처리 시작: 타입 = {}", messageType);
            switch (messageType) {
                case "subscribe":
                    handleSubscribeMessage(session, data);
                    break;
                case "boarding":
                    handleBoardingMessage(session, data);
                    break;
                case "location":
                    handleLocationMessage(session, data);
                    break;
                case "heartbeat":
                    handleHeartbeat(session);
                    break;
                case "get_bus_status":
                    handleGetBusStatus(session, data);
                    break;
                case "get_seat_info":
                    handleGetSeatInfo(session, data);
                    break;
                case "batch_boarding":
                    handleBatchBoardingMessage(session, data);
                    break;
                default:
                    log.warn("❓ [승객WebSocket] 알 수 없는 메시지 타입: {} - 세션 ID = {}",
                            messageType, sessionId);
                    sendErrorMessage(session, "알 수 없는 메시지 타입: " + messageType);
            }

        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 메시지 처리 중 오류: 세션 ID = {}, 페이로드 = {}, 오류 = {}",
                    sessionId, payload, e.getMessage(), e);
            sendErrorMessage(session, "메시지 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 다수 승객 탑승/하차 처리 (버스 기사용)
     */
    private void handleBatchBoardingMessage(WebSocketSession session, Map<String, Object> data) {
        log.info("👥 [다수탑승/하차] 메시지 처리 시작 - 세션 ID: {}", session.getId());

        try {
            Map<String, Object> batchData = (Map<String, Object>) data.get("data");
            if (batchData == null) {
                sendErrorMessage(session, "배치 탑승/하차 데이터가 필요합니다.");
                return;
            }

            String busNumber = (String) batchData.get("busNumber");
            String actionStr = (String) batchData.get("action");
            Integer count = getIntegerValue(batchData.get("count"));
            String organizationId = (String) data.get("organizationId");

            log.info("👥 [다수탑승/하차] 정보 - 버스: {}, 액션: {}, 인원: {}명",
                    busNumber, actionStr, count);

            // 검증
            if (busNumber == null || actionStr == null || count == null || count < 1) {
                sendErrorMessage(session, "버스 번호, 액션, 인원수(1명 이상)가 필요합니다.");
                return;
            }

            if (count > 100) {
                sendErrorMessage(session, "한 번에 처리할 수 있는 최대 인원은 100명입니다.");
                return;
            }

            // 액션 검증
            BusBoardingDTO.BoardingAction action;
            try {
                action = BusBoardingDTO.BoardingAction.valueOf(actionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                sendErrorMessage(session, "유효하지 않은 액션입니다. BOARD 또는 ALIGHT를 사용하세요.");
                return;
            }

            // 처리 전 상태
            BusSeatDTO beforeSeatInfo = getBusService().getBusSeatsByBusNumber(busNumber, organizationId);

            // 각 승객에 대해 처리
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < count; i++) {
                BusBoardingDTO boardingDTO = new BusBoardingDTO();
                boardingDTO.setBusNumber(busNumber);
                boardingDTO.setOrganizationId(organizationId);
                boardingDTO.setUserId("batch_user_" + System.currentTimeMillis() + "_" + i);
                boardingDTO.setAction(action);
                boardingDTO.setTimestamp(System.currentTimeMillis());

                boolean success = getBusService().processBusBoarding(boardingDTO);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                    // 더 이상 처리할 수 없으면 중단
                    break;
                }
            }

            // 처리 후 상태
            BusSeatDTO afterSeatInfo = getBusService().getBusSeatsByBusNumber(busNumber, organizationId);

            // 응답 메시지
            Map<String, Object> response = Map.of(
                    "type", "batch_boarding_response",
                    "status", failCount == 0 ? "success" : "partial",
                    "message", String.format("%d명 처리 완료, %d명 실패", successCount, failCount),
                    "busNumber", busNumber,
                    "action", action.name(),
                    "requested", count,
                    "processed", successCount,
                    "failed", failCount,
                    "seatInfo", Map.of(
                            "before", Map.of(
                                    "occupiedSeats", beforeSeatInfo.getOccupiedSeats(),
                                    "availableSeats", beforeSeatInfo.getAvailableSeats()
                            ),
                            "after", Map.of(
                                    "occupiedSeats", afterSeatInfo.getOccupiedSeats(),
                                    "availableSeats", afterSeatInfo.getAvailableSeats(),
                                    "totalSeats", afterSeatInfo.getTotalSeats()
                            ),
                            "occupancyRate", String.format("%.1f%%",
                                    (double) afterSeatInfo.getOccupiedSeats() / afterSeatInfo.getTotalSeats() * 100)
                    ),
                    "timestamp", System.currentTimeMillis()
            );

            sendMessage(session, response);

            log.info("👥 [다수탑승/하차] 처리 완료 - 성공: {}, 실패: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("❌ [다수탑승/하차] 처리 중 오류", e);
            sendErrorMessage(session, "다수 탑승/하차 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean needsOrganizationId(String messageType) {
        return !"heartbeat".equals(messageType);
    }

    private void registerSession(WebSocketSession session, String organizationId) {
        sessionToOrgMap.put(session.getId(), organizationId);
        organizationSessions.computeIfAbsent(organizationId, k -> ConcurrentHashMap.newKeySet())
                .add(session);

        log.info("✅ [승객WebSocket] 승객 세션 등록 완료: 조직 ID = {}, 세션 ID = {}",
                organizationId, session.getId());
    }

    /**
     * 승객 위치 메시지 처리 - 배터리 최적화 고려
     */
    private void handleLocationMessage(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();
        log.info("📍 [승객WebSocket] 위치 메시지 처리 시작: 세션 ID = {}", sessionId);

        try {
            Map<String, Object> locationData = (Map<String, Object>) data.get("data");

            log.debug("🗺️ [승객WebSocket] 위치 데이터 추출: {}", locationData);

            if (locationData == null) {
                log.warn("❌ [승객WebSocket] 위치 데이터 누락: 세션 ID = {}", sessionId);
                sendErrorMessage(session, "위치 데이터가 필요합니다.");
                return;
            }

            // 데이터 추출 및 검증
            String userId = (String) locationData.get("userId");
            Double latitude = getDoubleValue(locationData.get("latitude"));
            Double longitude = getDoubleValue(locationData.get("longitude"));

            log.info("👤 [승객WebSocket] 위치 정보 추출: 사용자 ID = {}, 위도 = {}, 경도 = {}",
                    userId, latitude, longitude);

            if (userId == null || latitude == null || longitude == null) {
                log.warn("❌ [승객WebSocket] 필수 필드 누락: 사용자 ID = {}, 위도 = {}, 경도 = {}, 세션 ID = {}",
                        userId, latitude, longitude, sessionId);
                sendErrorMessage(session, "사용자 ID, 위도, 경도가 필요합니다.");
                return;
            }

            // GPS 좌표 유효성 검증
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                log.warn("❌ [승객WebSocket] 잘못된 GPS 좌표: 위도 = {}, 경도 = {}, 세션 ID = {}",
                        latitude, longitude, sessionId);
                sendErrorMessage(session, "유효하지 않은 GPS 좌표입니다.");
                return;
            }

            // 한국 좌표계 범위 체크 (추가 검증)
            if (latitude < 33.0 || latitude > 39.0 || longitude < 124.0 || longitude > 132.0) {
                log.warn("⚠️ [승객WebSocket] 한국 외부 좌표: 위도 = {}, 경도 = {}, 세션 ID = {}",
                        latitude, longitude, sessionId);
                // 경고만 하고 처리는 계속
            }

            PassengerLocationDTO locationDTO = new PassengerLocationDTO();
            locationDTO.setUserId(userId);
            locationDTO.setOrganizationId((String) data.get("organizationId"));
            locationDTO.setLatitude(latitude);
            locationDTO.setLongitude(longitude);
            locationDTO.setTimestamp(System.currentTimeMillis());

            log.info("📋 [승객WebSocket] PassengerLocationDTO 생성 완료: {}", locationDTO);

            // 사용자 ID 저장
            sessionToUserMap.put(sessionId, userId);
            log.debug("💾 [승객WebSocket] 사용자 ID 저장: 세션 ID = {}, 사용자 ID = {}", sessionId, userId);

            // ========================= [수정된 부분 시작] =========================
            log.info("🚀 [승객WebSocket] PassengerLocationService 호출 시작");
            // 위치 처리 서비스 호출, 자동 탑승/하차 감지
            PassengerLocationService.DetectionResult result = getPassengerLocationService().processPassengerLocation(locationDTO);

            log.info("🎯 [승객WebSocket] 위치 처리 완료: 감지 결과 = {}, 사용자 ID = {}",
                    result, userId);

            // 자동 탑승/하차 감지 결과에 따라 프론트엔드에 메시지 전송
            switch (result) {
                case BOARDED:
                    PassengerLocationService.PassengerState state = getPassengerLocationService().getPassengerState(locationDTO.getUserId());
                    String boardedBusNumber = state != null ? state.getCurrentBusNumber() : "정보 없음";
                    log.info("🎉 [승객WebSocket] 자동 탑승 감지! 사용자 ID = {}, 버스 번호 = {}", userId, boardedBusNumber);
                    // 구조화된 탑승 성공 메시지 전송
                    sendMessage(session, Map.of(
                            "type", "boarding_update",
                            "status", "boarded",
                            "data", Map.of("busNumber", boardedBusNumber)
                    ));
                    break;
                case ALIGHTED:
                    log.info("🎉 [승객WebSocket] 자동 하차 감지! 사용자 ID = {}", userId);
                    // 구조화된 하차 성공 메시지 전송
                    sendMessage(session, Map.of(
                            "type", "boarding_update",
                            "status", "alighted"
                    ));
                    break;
                case NO_CHANGE:
                    log.debug("📍 [승객WebSocket] 일반 위치 업데이트 처리됨 (상태 변화 없음): 사용자 ID = {}", userId);
                    // 변화 없을 시에는 별도 메시지를 보내지 않아도 됨
                    break;
            }

        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 위치 메시지 처리 중 오류: 세션 ID = {}, 오류 = {}",
                    sessionId, e.getMessage(), e);
            sendErrorMessage(session, "위치 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private Double getDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("❌ [승객WebSocket] Double 변환 실패: value = {}", value);
            return null;
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = sessionToUserMap.get(session.getId());
        String organizationId = sessionToOrgMap.get(session.getId());

        log.error("⚠️ [승객WebSocket] 통신 오류: 세션 ID = {}, 사용자 ID = {}, 조직 ID = {}, 오류 = {}",
                session.getId(), userId, organizationId, exception.getMessage());

        if (exception != null) {
            log.error("🔍 [승객WebSocket] 상세 스택 트레이스:", exception);
        }

        // 오류 발생 시 세션 정리
        try {
            session.close();
            log.info("🛑 [승객WebSocket] 오류로 인한 세션 강제 종료: 세션 ID = {}", session.getId());
        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 세션 강제 종료 실패: 세션 ID = {}, 오류 = {}",
                    session.getId(), e.getMessage());
        }
    }

    /**
     * 특정 조직의 모든 승객에게 버스 상태 업데이트 전송
     * - 네트워크 효율성을 위한 배치 전송
     */
    public void broadcastBusStatus(String organizationId, BusRealTimeStatusDTO busStatus) {
        log.info("📢 [승객WebSocket] 버스 상태 브로드캐스트 시작: 조직 ID = {}", organizationId);

        Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
        if (sessions != null && !sessions.isEmpty()) {
            Map<String, Object> message = Map.of(
                    "type", "busUpdate",
                    "data", busStatus,
                    "timestamp", System.currentTimeMillis()
            );

            log.info("📤 [승객WebSocket] {}개 세션에 메시지 전송 시작", sessions.size());

            // 병렬 처리로 성능 향상
            sessions.parallelStream().forEach(session -> {
                if (session.isOpen()) {
                    try {
                        sendMessage(session, message);
                        log.debug("✅ [승객WebSocket] 메시지 전송 성공: 세션 ID = {}", session.getId());
                    } catch (Exception e) {
                        log.error("❌ [승객WebSocket] 버스 상태 업데이트 전송 실패: 세션 ID = {}, 오류 = {}",
                                session.getId(), e.getMessage());

                        // 전송 실패한 세션은 정리 대상으로 표시
                        markSessionForCleanup(session);
                    }
                }
            });

            log.info("📊 [승객WebSocket] 조직 {}의 {}명의 승객에게 버스 상태 업데이트 전송 완료",
                    organizationId, sessions.size());
        } else {
            log.debug("📭 [승객WebSocket] 브로드캐스트할 세션 없음: 조직 ID = {}", organizationId);
        }
    }

    /**
     * 조직별 활성 승객 수 조회
     */
    public int getActivePassengerCount(String organizationId) {
        Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
        if (sessions == null) return 0;

        // 유효한 세션만 카운트
        int count = (int) sessions.stream()
                .filter(session -> session != null && session.isOpen())
                .count();

        log.debug("📊 [승객WebSocket] 조직별 활성 승객 수: 조직 ID = {}, 승객 수 = {}", organizationId, count);
        return count;
    }

    /**
     * 전체 활성 승객 수 조회
     */
    public int getTotalActivePassengerCount() {
        int totalCount = organizationSessions.values().stream()
                .mapToInt(sessions -> (int) sessions.stream()
                        .filter(session -> session != null && session.isOpen())
                        .count())
                .sum();

        log.debug("📊 [승객WebSocket] 전체 활성 승객 수: {}", totalCount);
        return totalCount;
    }

    /**
     * 구독 메시지 처리 - 초기 데이터 제공
     */
    private void handleSubscribeMessage(WebSocketSession session, Map<String, Object> data) {
        String organizationId = (String) data.get("organizationId");
        log.info("📧 [승객WebSocket] 구독 메시지 처리 시작: 조직 ID = {}, 세션 ID = {}",
                organizationId, session.getId());

        try {
            // 해당 조직의 모든 버스 상태 즉시 전송
            getBusService().getAllBusStatusByOrganizationId(organizationId).forEach(busStatus -> {
                try {
                    Map<String, Object> message = Map.of(
                            "type", "busUpdate",
                            "data", busStatus,
                            "timestamp", System.currentTimeMillis()
                    );
                    sendMessage(session, message);
                    log.debug("📤 [승객WebSocket] 초기 버스 상태 전송: 버스 ID = {}", busStatus.getBusId());
                } catch (Exception e) {
                    log.error("❌ [승객WebSocket] 초기 버스 상태 전송 실패: 오류 = {}", e.getMessage());
                }
            });

            sendSuccessMessage(session, "구독이 성공적으로 등록되었습니다.");
            log.info("✅ [승객WebSocket] 구독 처리 완료: 조직 ID = {}", organizationId);

        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 구독 처리 중 오류: 조직 ID = {}, 오류 = {}",
                    organizationId, e.getMessage());
            sendErrorMessage(session, "구독 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 승객 탑승/하차 메시지 처리 - 좌석 수 실시간 업데이트 포함
     */
    private void handleBoardingMessage(WebSocketSession session, Map<String, Object> data) {
        log.info("🚌 [탑승/하차] ============= 메시지 처리 시작 =============");
        log.info("🚌 [탑승/하차] 세션 ID: {}", session.getId());

        try {
            // 1. 데이터 추출
            Map<String, Object> boardingData = (Map<String, Object>) data.get("data");
            if (boardingData == null) {
                log.warn("❌ [탑승/하차] 탑승/하차 데이터 누락: 세션 ID = {}", session.getId());
                sendErrorMessage(session, "탑승/하차 데이터가 필요합니다.");
                return;
            }

            String busNumber = (String) boardingData.get("busNumber");
            String userId = (String) boardingData.get("userId");
            String actionStr = (String) boardingData.get("action");
            String organizationId = (String) data.get("organizationId");

            log.info("🎫 [탑승/하차] 정보 추출 - 버스: {}, 사용자: {}, 액션: {}, 조직: {}",
                    busNumber, userId, actionStr, organizationId);

            // 2. 필수 필드 검증
            if (busNumber == null || userId == null || actionStr == null) {
                log.warn("❌ [탑승/하차] 필수 필드 누락");
                sendErrorMessage(session, "버스 번호, 사용자 ID, 액션이 필요합니다.");
                return;
            }

            // 3. DTO 생성
            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber(busNumber);
            boardingDTO.setOrganizationId(organizationId);
            boardingDTO.setUserId(userId);

            try {
                boardingDTO.setAction(BusBoardingDTO.BoardingAction.valueOf(actionStr.toUpperCase()));
                log.info("✅ [탑승/하차] 액션 설정 완료: {}", boardingDTO.getAction());
            } catch (IllegalArgumentException e) {
                log.warn("❌ [탑승/하차] 유효하지 않은 액션: {}", actionStr);
                sendErrorMessage(session, "유효하지 않은 액션입니다. BOARD 또는 ALIGHT를 사용하세요.");
                return;
            }

            boardingDTO.setTimestamp(System.currentTimeMillis());

            // 4. 처리 전 버스 상태 조회 (비교용)
            BusSeatDTO beforeSeatInfo = getBusService().getBusSeatsByBusNumber(busNumber, organizationId);
            log.info("📊 [탑승/하차] 처리 전 좌석 상태 - 사용중: {}/{}, 가능: {}",
                    beforeSeatInfo.getOccupiedSeats(),
                    beforeSeatInfo.getTotalSeats(),
                    beforeSeatInfo.getAvailableSeats());

            // 5. BusService를 통한 탑승/하차 처리
            log.info("🚀 [탑승/하차] BusService.processBusBoarding 호출");
            boolean success = getBusService().processBusBoarding(boardingDTO);

            log.info("🎯 [탑승/하차] 처리 결과: {}", success ? "성공" : "실패");

            // 6. 처리 후 버스 상태 조회
            BusSeatDTO afterSeatInfo = getBusService().getBusSeatsByBusNumber(busNumber, organizationId);

            // 7. 응답 메시지 생성
            if (success) {
                String actionMessage = boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD ?
                        "탑승이 완료되었습니다." : "하차가 완료되었습니다.";

                // 좌석 변화 정보 계산
                int seatChange = Math.abs(afterSeatInfo.getOccupiedSeats() - beforeSeatInfo.getOccupiedSeats());

                // 성공 응답 (좌석 정보 포함)
                Map<String, Object> successResponse = Map.of(
                        "type", "boarding_response",
                        "status", "success",
                        "message", actionMessage,
                        "action", boardingDTO.getAction().name(),
                        "busNumber", busNumber,
                        "userId", userId,
                        "seatInfo", Map.of(
                                "before", Map.of(
                                        "occupiedSeats", beforeSeatInfo.getOccupiedSeats(),
                                        "availableSeats", beforeSeatInfo.getAvailableSeats()
                                ),
                                "after", Map.of(
                                        "occupiedSeats", afterSeatInfo.getOccupiedSeats(),
                                        "availableSeats", afterSeatInfo.getAvailableSeats(),
                                        "totalSeats", afterSeatInfo.getTotalSeats()
                                ),
                                "change", seatChange,
                                "occupancyRate", String.format("%.1f%%",
                                        (double) afterSeatInfo.getOccupiedSeats() / afterSeatInfo.getTotalSeats() * 100)
                        ),
                        "timestamp", System.currentTimeMillis()
                );

                sendMessage(session, successResponse);

                log.info("🎉 [탑승/하차] {} 성공 - 좌석 변화: {} -> {} ({}{})",
                        boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD ? "탑승" : "하차",
                        beforeSeatInfo.getOccupiedSeats(),
                        afterSeatInfo.getOccupiedSeats(),
                        boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD ? "+" : "-",
                        seatChange);

                // 거의 만석/만석 상태 추가 알림
                if (afterSeatInfo.getAvailableSeats() == 0) {
                    sendWarningMessage(session, "⚠️ 버스가 만석입니다!");
                } else if (afterSeatInfo.getAvailableSeats() <= 5) {
                    sendWarningMessage(session, String.format("⚠️ 잔여 좌석 %d석", afterSeatInfo.getAvailableSeats()));
                }

            } else {
                // 실패 이유 분석
                String failureReason;
                if (!afterSeatInfo.isOperate()) {
                    failureReason = "운행 중이 아닌 버스입니다.";
                } else if (boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD
                        && afterSeatInfo.getAvailableSeats() == 0) {
                    failureReason = "버스가 만석입니다.";
                } else if (boardingDTO.getAction() == BusBoardingDTO.BoardingAction.ALIGHT
                        && afterSeatInfo.getOccupiedSeats() == 0) {
                    failureReason = "버스에 탑승한 승객이 없습니다.";
                } else {
                    failureReason = "처리할 수 없는 요청입니다.";
                }

                // 실패 응답 (현재 좌석 정보 포함)
                Map<String, Object> failureResponse = Map.of(
                        "type", "boarding_response",
                        "status", "failure",
                        "message", failureReason,
                        "action", boardingDTO.getAction().name(),
                        "busNumber", busNumber,
                        "currentSeatInfo", Map.of(
                                "occupiedSeats", afterSeatInfo.getOccupiedSeats(),
                                "availableSeats", afterSeatInfo.getAvailableSeats(),
                                "totalSeats", afterSeatInfo.getTotalSeats(),
                                "isOperating", afterSeatInfo.isOperate()
                        ),
                        "timestamp", System.currentTimeMillis()
                );

                sendMessage(session, failureResponse);

                log.warn("⚠️ [탑승/하차] 처리 실패 - 이유: {}", failureReason);
            }

            log.info("🚌 [탑승/하차] ============= 메시지 처리 완료 =============");

        } catch (Exception e) {
            log.error("❌ [탑승/하차] 메시지 처리 중 오류", e);
            sendErrorMessage(session, "탑승/하차 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 경고 메시지 전송 (좌석 부족 등)
     */
    private void sendWarningMessage(WebSocketSession session, String warningMessage) {
        log.warn("⚠️ [경고] 메시지 전송: 세션 ID = {}, 메시지 = {}",
                session.getId(), warningMessage);

        try {
            Map<String, Object> response = Map.of(
                    "type", "warning",
                    "message", warningMessage,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
        } catch (Exception e) {
            log.error("❌ [경고] 메시지 전송 중 오류", e);
        }
    }

    /**
     * 좌석 정보 조회 메시지 처리
     */
    private void handleGetSeatInfo(WebSocketSession session, Map<String, Object> data) {
        log.info("💺 [좌석정보] 조회 요청 - 세션 ID: {}", session.getId());

        try {
            String busNumber = (String) data.get("busNumber");
            String organizationId = (String) data.get("organizationId");

            if (busNumber == null || organizationId == null) {
                sendErrorMessage(session, "버스 번호와 조직 ID가 필요합니다.");
                return;
            }

            // 좌석 정보 조회
            BusSeatDTO seatInfo = getBusService().getBusSeatsByBusNumber(busNumber, organizationId);

            // 좌석 상태 메시지 생성
            String statusMessage;
            String statusLevel;
            if (seatInfo.getAvailableSeats() == 0) {
                statusMessage = "만석";
                statusLevel = "critical";
            } else if (seatInfo.getAvailableSeats() <= 5) {
                statusMessage = String.format("잔여 %d석", seatInfo.getAvailableSeats());
                statusLevel = "warning";
            } else {
                statusMessage = String.format("여유 %d석", seatInfo.getAvailableSeats());
                statusLevel = "normal";
            }

            // 응답 메시지
            Map<String, Object> response = Map.of(
                    "type", "seat_info_response",
                    "busNumber", seatInfo.getBusNumber(),
                    "busRealNumber", seatInfo.getBusRealNumber() != null ? seatInfo.getBusRealNumber() : "",
                    "seatInfo", Map.of(
                            "totalSeats", seatInfo.getTotalSeats(),
                            "occupiedSeats", seatInfo.getOccupiedSeats(),
                            "availableSeats", seatInfo.getAvailableSeats(),
                            "occupancyRate", String.format("%.1f%%",
                                    (double) seatInfo.getOccupiedSeats() / seatInfo.getTotalSeats() * 100),
                            "statusMessage", statusMessage,
                            "statusLevel", statusLevel,
                            "isOperating", seatInfo.isOperate()
                    ),
                    "timestamp", System.currentTimeMillis()
            );

            sendMessage(session, response);

            log.info("💺 [좌석정보] 조회 완료 - 버스: {}, 사용중: {}/{}",
                    busNumber, seatInfo.getOccupiedSeats(), seatInfo.getTotalSeats());

        } catch (Exception e) {
            log.error("❌ [좌석정보] 조회 중 오류", e);
            sendErrorMessage(session, "좌석 정보 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 하트비트 처리
     */
    private void handleHeartbeat(WebSocketSession session) {
        log.debug("🏓 [승객WebSocket] 하트비트 수신: 세션 ID = {}", session.getId());

        try {
            sendMessage(session, Map.of(
                    "type", "heartbeat_response",
                    "status", "alive",
                    "timestamp", System.currentTimeMillis()
            ));
            log.debug("🏓 [승객WebSocket] 하트비트 응답 전송: 세션 ID = {}", session.getId());
        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 하트비트 응답 전송 실패: 세션 ID = {}, 오류 = {}",
                    session.getId(), e.getMessage());
        }
    }

    /**
     * 버스 상태 조회 처리
     */
    private void handleGetBusStatus(WebSocketSession session, Map<String, Object> data) {
        String organizationId = (String) data.get("organizationId");
        String busNumber = (String) data.get("busNumber");

        log.info("🔍 [승객WebSocket] 버스 상태 조회 요청: 조직 ID = {}, 버스 번호 = {}, 세션 ID = {}",
                organizationId, busNumber, session.getId());

        try {
            if (busNumber != null) {
                log.warn("⚠️ [승객WebSocket] 특정 버스 상태 조회 미구현: 버스 번호 = {}", busNumber);
                // 특정 버스 상태 조회
                // 구현 필요: BusService에서 특정 버스 상태 조회 메서드 추가
                sendErrorMessage(session, "특정 버스 상태 조회는 아직 구현되지 않았습니다.");
            } else {
                log.info("📋 [승객WebSocket] 전체 버스 상태 조회로 처리");
                // 전체 버스 상태 조회
                handleSubscribeMessage(session, data);
            }
        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 버스 상태 조회 중 오류: 조직 ID = {}, 오류 = {}",
                    organizationId, e.getMessage(), e);
            sendErrorMessage(session, "버스 상태 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 성공 메시지 전송
     */
    private void sendSuccessMessage(WebSocketSession session, String message) {
        log.info("✅ [승객WebSocket] 성공 메시지 전송: 세션 ID = {}, 메시지 = {}",
                session.getId(), message);

        try {
            Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", message,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 성공 메시지 전송 중 오류: 세션 ID = {}, 오류 = {}",
                    session.getId(), e.getMessage());
        }
    }

    /**
     * 오류 메시지 전송
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        log.warn("⚠️ [승객WebSocket] 오류 메시지 전송: 세션 ID = {}, 메시지 = {}",
                session.getId(), errorMessage);

        try {
            Map<String, Object> response = Map.of(
                    "status", "error",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 오류 메시지 전송 중 오류: 세션 ID = {}, 원본 메시지 = {}, 전송 오류 = {}",
                    session.getId(), errorMessage, e.getMessage());
        }
    }

    /**
     * 세션에 메시지 전송 (내부 헬퍼 메서드)
     */
    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (session != null && session.isOpen()) {
            String jsonMessage = objectMapper.writeValueAsString(message);
            log.debug("📤 [승객WebSocket] 메시지 전송: 세션 ID = {}, 크기 = {}bytes",
                    session.getId(), jsonMessage.length());

            synchronized (session) { // 동시성 문제 방지
                session.sendMessage(new TextMessage(jsonMessage));
            }

            log.debug("✅ [승객WebSocket] 메시지 전송 완료: 세션 ID = {}", session.getId());
        } else {
            log.warn("❌ [승객WebSocket] 메시지 전송 실패 - 세션 닫힘: 세션 ID = {}",
                    session != null ? session.getId() : "null");
        }
    }

    /**
     * 비활성 세션들 정리 (10분 이상 비활성)
     */
    private void cleanupInactiveSessions() {
        Instant threshold = Instant.now().minusSeconds(600); // 10분 임계값
        log.info("🧹 [승객WebSocket] 비활성 세션 정리 시작: 임계값 = {}", threshold);

        int removedCount = 0;

        for (Map.Entry<String, Instant> entry : lastActivityMap.entrySet()) {
            String sessionId = entry.getKey();
            Instant lastActivity = entry.getValue();

            if (lastActivity.isBefore(threshold)) {
                log.info("🗑️ [승객WebSocket] 비활성 세션 발견: 세션 ID = {}, 마지막 활동 = {}",
                        sessionId, lastActivity);

                // 세션 정리
                String organizationId = sessionToOrgMap.remove(sessionId);
                String userId = sessionToUserMap.remove(sessionId);

                if (organizationId != null) {
                    Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
                    if (sessions != null) {
                        sessions.removeIf(session -> session.getId().equals(sessionId));
                        if (sessions.isEmpty()) {
                            organizationSessions.remove(organizationId);
                            log.info("🗑️ [승객WebSocket] 조직 세션 맵 제거: 조직 ID = {}", organizationId);
                        }
                    }
                }

                removedCount++;
                log.info("🧹 [승객WebSocket] 세션 정리 완료: 세션 ID = {}, 조직 ID = {}, 사용자 ID = {}",
                        sessionId, organizationId, userId);
            }
        }

        // 정리된 세션들을 lastActivityMap에서도 제거
        lastActivityMap.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));

        if (removedCount > 0) {
            log.info("✅ [승객WebSocket] 비활성 세션 정리 완료: {}개 세션 제거", removedCount);
        } else {
            log.debug("📊 [승객WebSocket] 정리할 비활성 세션 없음");
        }
    }

    /**
     * 문제 있는 세션을 정리 대상으로 표시
     */
    private void markSessionForCleanup(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("🏷️ [승객WebSocket] 세션을 정리 대상으로 표시: 세션 ID = {}", sessionId);

        // 즉시 정리하지 않고 다음 정리 주기에서 처리되도록 활동 시간을 오래 전으로 설정
        lastActivityMap.put(sessionId, Instant.now().minusSeconds(700));

        log.debug("⏰ [승객WebSocket] 세션 활동 시간을 과거로 설정: 세션 ID = {}", sessionId);
    }

    /**
     * 셧다운 훅 - 애플리케이션 종료 시 리소스 정리
     */
    @PreDestroy
    public void shutdown() {
        log.info("🛑 [승객WebSocket] BusPassengerWebSocketHandler 종료 시작...");

        // 모든 세션 정리
        int totalSessions = 0;
        for (Set<WebSocketSession> sessions : organizationSessions.values()) {
            totalSessions += sessions.size();
            sessions.forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.close(CloseStatus.GOING_AWAY);
                        log.debug("🔌 [승객WebSocket] 세션 종료: 세션 ID = {}", session.getId());
                    }
                } catch (Exception e) {
                    log.error("❌ [승객WebSocket] 세션 종료 중 오류: 세션 ID = {}, 오류 = {}",
                            session.getId(), e.getMessage());
                }
            });
        }

        log.info("🧹 [승객WebSocket] 총 {}개 세션 종료 완료", totalSessions);

        // 스케줄러 종료
        log.info("⏹️ [승객WebSocket] 스케줄러 종료 시작");
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("⚠️ [승객WebSocket] 스케줄러 정상 종료 실패 - 강제 종료");
                cleanupScheduler.shutdownNow();
            } else {
                log.info("✅ [승객WebSocket] 스케줄러 정상 종료 완료");
            }
        } catch (InterruptedException e) {
            log.warn("⚠️ [승객WebSocket] 스케줄러 종료 중 인터럽트 발생 - 강제 종료");
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("✅ [승객WebSocket] BusPassengerWebSocketHandler 종료 완료");
    }
}