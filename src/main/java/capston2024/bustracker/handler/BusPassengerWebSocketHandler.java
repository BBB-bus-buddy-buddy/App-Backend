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
 */
@Slf4j
@Component
public class BusPassengerWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 세션 관리용 맵들
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToOrganization = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastActivityMap = new ConcurrentHashMap<>();

    @Autowired
    private PassengerLocationService passengerLocationService;

    // =================================
    // 🔗 WebSocket 연결 관리
    // =================================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        String remoteAddress = getRemoteAddress(session);

        log.info("🟢 [승객WebSocket] 연결 설정: 세션 ID = {}, IP = {}", sessionId, remoteAddress);

        // 세션 저장
        sessions.put(sessionId, session);
        lastActivityMap.put(sessionId, Instant.now());

        // 연결 성공 메시지 전송
        sendMessage(session, Map.of(
                "type", "connection_established",
                "sessionId", sessionId,
                "timestamp", System.currentTimeMillis()
        ));

        log.info("✅ [승객WebSocket] 연결 완료 및 환영 메시지 전송: {}", sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        String organizationId = sessionToOrganization.get(sessionId);
        String userId = sessionToUserId.get(sessionId);

        log.info("🔴 [승객WebSocket] 연결 종료: 세션 ID = {}, 조직 ID = {}, 사용자 ID = {}, 상태 = {}",
                sessionId, organizationId, userId, status.getCode());

        // 세션 정리
        sessions.remove(sessionId);
        sessionToOrganization.remove(sessionId);
        sessionToUserId.remove(sessionId);
        lastActivityMap.remove(sessionId);

        log.info("🧹 [승객WebSocket] 세션 정리 완료: {}", sessionId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        String userId = sessionToUserId.get(sessionId);
        String organizationId = sessionToOrganization.get(sessionId);

        log.error("⚠️ [승객WebSocket] 통신 오류: 세션 ID = {}, 사용자 ID = {}, 조직 ID = {}, 오류 = {}",
                sessionId, userId, organizationId, exception.getMessage());

        if (exception != null) {
            log.error("🔍 [승객WebSocket] 상세 스택 트레이스:", exception);
        }
    }

    // =================================
    // 📨 메시지 처리
    // =================================

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String sessionId = session.getId();

        // 🔍 기본 수신 로그
        log.info("📥 [승객WebSocket] 메시지 수신 - 세션: {}, 크기: {}bytes, 내용: {}",
                sessionId, payload.length(), payload);

        // 활동 시간 업데이트
        lastActivityMap.put(sessionId, Instant.now());

        try {
            // JSON 파싱
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String messageType = (String) data.get("type");
            String organizationId = (String) data.get("organizationId");

            // 🔍 파싱 결과 로그
            log.info("🔧 [승객WebSocket] 메시지 파싱 완료 - 타입: {}, 조직: {}, 세션: {}",
                    messageType, organizationId, sessionId);

            // 메시지 타입별 처리
            switch (messageType) {
                case "register":
                    handleRegisterMessage(session, data);
                    break;
                case "location":
                    handleLocationMessage(session, data);
                    break;
                case "ping":
                    handlePingMessage(session);
                    break;
                default:
                    log.warn("❓ [승객WebSocket] 알 수 없는 메시지 타입: {} - 세션: {}", messageType, sessionId);
                    sendErrorMessage(session, "지원하지 않는 메시지 타입입니다: " + messageType);
            }

        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 메시지 처리 중 오류: 세션={}, 페이로드={}, 오류={}",
                    sessionId, payload, e.getMessage(), e);
            sendErrorMessage(session, "메시지 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // =================================
    // 📝 승객 등록 처리
    // =================================

    private void handleRegisterMessage(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();
        String organizationId = (String) data.get("organizationId");
        String userId = (String) data.get("userId");

        log.info("👤 [승객WebSocket] 승객 세션 등록 시도 - 조직 ID: {}, 사용자 ID: {}, 세션: {}",
                organizationId, userId, sessionId);

        // 필수 정보 검증
        if (organizationId == null || organizationId.trim().isEmpty()) {
            log.warn("❌ [승객WebSocket] 조직 ID 누락 - 세션: {}", sessionId);
            sendErrorMessage(session, "조직 ID가 필요합니다.");
            return;
        }

        if (userId == null || userId.trim().isEmpty()) {
            log.warn("❌ [승객WebSocket] 사용자 ID 누락 - 세션: {}", sessionId);
            sendErrorMessage(session, "사용자 ID가 필요합니다.");
            return;
        }

        // 세션 정보 저장
        sessionToOrganization.put(sessionId, organizationId);
        sessionToUserId.put(sessionId, userId);

        log.info("✅ [승객WebSocket] 승객 세션 등록 완료 - 조직 ID = {}, 사용자 ID = {}, 세션 ID = {}",
                organizationId, userId, sessionId);

        // 등록 성공 응답
        sendMessage(session, Map.of(
                "type", "register_success",
                "organizationId", organizationId,
                "userId", userId,
                "sessionId", sessionId,
                "timestamp", System.currentTimeMillis()
        ));
    }

    // =================================
    // 📍 위치 메시지 처리 (핵심 기능)
    // =================================

    private void handleLocationMessage(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();

        log.info("📍 [승객WebSocket] 위치 메시지 처리 시작 - 세션: {}", sessionId);

        try {
            // 위치 데이터 추출
            Map<String, Object> locationData = (Map<String, Object>) data.get("data");

            log.info("🗺️ [승객WebSocket] 위치 데이터 추출 성공: {}", locationData);

            if (locationData == null) {
                log.warn("❌ [승객WebSocket] 위치 데이터 없음 - 세션: {}", sessionId);
                sendErrorMessage(session, "위치 데이터가 필요합니다.");
                return;
            }

            // 필수 필드 추출 및 검증
            String userId = (String) locationData.get("userId");
            Double latitude = getDoubleValue(locationData.get("latitude"));
            Double longitude = getDoubleValue(locationData.get("longitude"));
            Long timestamp = getLongValue(locationData.get("timestamp"));

            log.info("👤 [승객WebSocket] 추출된 위치 정보 - 사용자: {}, 위도: {}, 경도: {}, 시간: {}",
                    userId, latitude, longitude, timestamp);

            // 필수 필드 유효성 검증
            if (userId == null || userId.trim().isEmpty()) {
                log.warn("❌ [승객WebSocket] 사용자 ID 누락 - 세션: {}", sessionId);
                sendErrorMessage(session, "사용자 ID가 필요합니다.");
                return;
            }

            if (latitude == null || longitude == null) {
                log.warn("❌ [승객WebSocket] 좌표 정보 누락 - 위도: {}, 경도: {}, 세션: {}",
                        latitude, longitude, sessionId);
                sendErrorMessage(session, "위도와 경도가 필요합니다.");
                return;
            }

            // GPS 좌표 유효성 검증
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                log.warn("❌ [승객WebSocket] 잘못된 GPS 좌표 - 위도: {}, 경도: {}, 세션: {}",
                        latitude, longitude, sessionId);
                sendErrorMessage(session, "유효하지 않은 GPS 좌표입니다.");
                return;
            }

            // 한국 좌표계 범위 체크 (추가 검증)
            if (latitude < 33.0 || latitude > 39.0 || longitude < 124.0 || longitude > 132.0) {
                log.warn("⚠️ [승객WebSocket] 한국 외부 좌표 - 위도: {}, 경도: {}, 세션: {}",
                        latitude, longitude, sessionId);
                // 경고만 하고 처리는 계속
            }

            // DTO 생성
            PassengerLocationDTO locationDTO = new PassengerLocationDTO();
            locationDTO.setUserId(userId);
            locationDTO.setOrganizationId((String) data.get("organizationId"));
            locationDTO.setLatitude(latitude);
            locationDTO.setLongitude(longitude);
            locationDTO.setTimestamp(timestamp != null ? timestamp : System.currentTimeMillis());

            log.info("🚀 [승객WebSocket] PassengerLocationService 호출 시작 - DTO: {}", locationDTO);

            // 위치 처리 서비스 호출
            boolean boardingDetected = passengerLocationService.processPassengerLocation(locationDTO);

            log.info("🎯 [승객WebSocket] 위치 처리 완료 - 탑승감지: {}, 사용자: {}, 세션: {}",
                    boardingDetected, userId, sessionId);

            // 결과에 따른 응답
            if (boardingDetected) {
                log.info("🎉 [승객WebSocket] 자동 탑승/하차 감지됨! - 사용자: {}", userId);
                sendSuccessMessage(session, "버스 탑승/하차가 자동으로 감지되었습니다.");

                // 추가로 특별한 알림이 필요한 경우
                sendMessage(session, Map.of(
                        "type", "boarding_detected",
                        "userId", userId,
                        "timestamp", System.currentTimeMillis(),
                        "message", "자동 탑승/하차 감지"
                ));
            } else {
                log.debug("📍 [승객WebSocket] 위치 처리됨 (탑승 감지 없음) - 사용자: {}", userId);
                sendMessage(session, Map.of(
                        "type", "location_processed",
                        "status", "success",
                        "userId", userId,
                        "timestamp", System.currentTimeMillis()
                ));
            }

        } catch (Exception e) {
            log.error("❌ [승객WebSocket] 위치 메시지 처리 중 오류: 세션={}, 오류={}",
                    sessionId, e.getMessage(), e);
            sendErrorMessage(session, "위치 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // =================================
    // 🏓 Ping/Pong 처리
    // =================================

    private void handlePingMessage(WebSocketSession session) {
        String sessionId = session.getId();
        log.debug("🏓 [승객WebSocket] Ping 수신 - 세션: {}", sessionId);

        sendMessage(session, Map.of(
                "type", "pong",
                "timestamp", System.currentTimeMillis()
        ));

        log.debug("🏓 [승객WebSocket] Pong 응답 전송 - 세션: {}", sessionId);
    }

    // =================================
    // 📤 메시지 전송 유틸리티
    // =================================

    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            if (session.isOpen()) {
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));

                log.debug("📤 [승객WebSocket] 메시지 전송 성공 - 세션: {}, 타입: {}",
                        session.getId(), message.get("type"));
            } else {
                log.warn("❌ [승객WebSocket] 세션 닫힘으로 메시지 전송 실패 - 세션: {}", session.getId());
            }
        } catch (IOException e) {
            log.error("❌ [승객WebSocket] 메시지 전송 중 오류: 세션={}, 오류={}",
                    session.getId(), e.getMessage(), e);
        }
    }

    private void sendSuccessMessage(WebSocketSession session, String message) {
        sendMessage(session, Map.of(
                "type", "success",
                "message", message,
                "timestamp", System.currentTimeMillis()
        ));
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        log.warn("⚠️ [승객WebSocket] 에러 메시지 전송 - 세션: {}, 메시지: {}", session.getId(), errorMessage);

        sendMessage(session, Map.of(
                "type", "error",
                "message", errorMessage,
                "timestamp", System.currentTimeMillis()
        ));
    }

    // =================================
    // 🛠️ 유틸리티 메서드
    // =================================

    private String getRemoteAddress(WebSocketSession session) {
        try {
            return session.getRemoteAddress() != null ?
                    session.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Double getDoubleValue(Object value) {
        if (value == null) return null;

        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        } catch (NumberFormatException e) {
            log.warn("❌ [승객WebSocket] Double 변환 실패: {}", value);
        }

        return null;
    }

    private Long getLongValue(Object value) {
        if (value == null) return null;

        try {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else if (value instanceof String) {
                return Long.parseLong((String) value);
            }
        } catch (NumberFormatException e) {
            log.warn("❌ [승객WebSocket] Long 변환 실패: {}", value);
        }

        return null;
    }

    // =================================
    // 📊 세션 관리 및 모니터링
    // =================================

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public int getOrganizationSessionCount(String organizationId) {
        return (int) sessionToOrganization.values().stream()
                .filter(org -> org.equals(organizationId))
                .count();
    }

    /**
     * 비활성 세션 정리 (스케줄러에서 호출)
     */
    public void cleanupInactiveSessions() {
        Instant cutoff = Instant.now().minusSeconds(300); // 5분

        lastActivityMap.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                String sessionId = entry.getKey();
                WebSocketSession session = sessions.get(sessionId);

                if (session != null) {
                    try {
                        log.info("🧹 [승객WebSocket] 비활성 세션 정리: {}", sessionId);
                        session.close(CloseStatus.GOING_AWAY);
                    } catch (IOException e) {
                        log.warn("❌ [승객WebSocket] 세션 종료 중 오류: {}", e.getMessage());
                    }
                }

                // 관련 맵에서도 제거
                sessions.remove(sessionId);
                sessionToOrganization.remove(sessionId);
                sessionToUserId.remove(sessionId);

                return true;
            }
            return false;
        });
    }

    /**
     * PassengerLocationService getter (늦은 초기화 방지)
     */
    private PassengerLocationService getPassengerLocationService() {
        if (passengerLocationService == null) {
            log.error("❌ [승객WebSocket] PassengerLocationService가 주입되지 않음!");
            throw new IllegalStateException("PassengerLocationService가 주입되지 않았습니다.");
        }
        return passengerLocationService;
    }
}