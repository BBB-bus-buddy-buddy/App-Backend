package capston2024.bustracker.handler;

import capston2024.bustracker.config.ConnectionLimitInterceptor;
import capston2024.bustracker.config.dto.BusRealTimeLocationDTO;
import capston2024.bustracker.service.BusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 개선된 버스 기사 앱과의 WebSocket 통신 핸들러
 * - 메모리 누수 방지
 * - 에러 처리 강화
 * - 하트비트 추가
 * - 성능 최적화
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BusDriverWebSocketHandler extends TextWebSocketHandler {

    private final BusService busService;
    private final ObjectMapper objectMapper;

    // 세션 관리를 위한 맵들 - 메모리 누수 방지를 위해 ConcurrentHashMap 사용
    private final Map<String, WebSocketSession> driverSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToBusMap = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHeartbeatMap = new ConcurrentHashMap<>();

    // 하트비트 체크를 위한 스케줄러
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");
        log.info("버스 기사 WebSocket 연결 설정: 세션 ID = {}, IP = {}", session.getId(), clientIp);

        // 하트비트 초기화
        lastHeartbeatMap.put(session.getId(), Instant.now());

        // 연결 성공 메시지 전송
        try {
            sendMessage(session, Map.of(
                    "type", "connection_established",
                    "status", "success",
                    "message", "웹소켓 연결이 성공적으로 설정되었습니다.",
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("연결 확인 메시지 전송 실패: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String busNumber = sessionToBusMap.remove(sessionId);
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");

        // 모든 맵에서 세션 정보 제거 (메모리 누수 방지)
        if (busNumber != null) {
            driverSessions.remove(busNumber);
            log.info("버스 기사 WebSocket 연결 종료: 세션 ID = {}, 버스 번호 = {}, 상태 = {}",
                    sessionId, busNumber, status.getCode());
        } else {
            log.info("버스 기사 WebSocket 연결 종료: 세션 ID = {}, 상태 = {}", sessionId, status.getCode());
        }

        // 하트비트 정보 제거
        lastHeartbeatMap.remove(sessionId);

        // IP별 연결 수 감소
        if (clientIp != null) {
            ConnectionLimitInterceptor.decrementConnection(clientIp);
        }

        // 버스 상태를 비활성으로 업데이트
        if (busNumber != null) {
            try {
                busService.updateBusInactiveStatus(busNumber);
            } catch (Exception e) {
                log.error("버스 비활성 상태 업데이트 실패: {}", e.getMessage());
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("버스 기사로부터 메시지 수신: 세션 ID = {}, 메시지 길이 = {}",
                session.getId(), payload.length());

        try {
            // 메시지 타입 판별
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            String messageType = (String) messageData.get("type");

            // 하트비트 업데이트
            lastHeartbeatMap.put(session.getId(), Instant.now());

            switch (messageType) {
                case "location_update":
                    handleLocationUpdate(session, messageData);
                    break;
                case "heartbeat":
                    handleHeartbeat(session);
                    break;
                case "bus_status_update":
                    handleBusStatusUpdate(session, messageData);
                    break;
                default:
                    // 기존 로직 (하위 호환성)
                    handleLegacyLocationUpdate(session, payload);
            }

        } catch (Exception e) {
            log.error("버스 위치 업데이트 처리 중 오류 발생: 세션 ID = {}, 오류 = {}",
                    session.getId(), e.getMessage(), e);

            sendErrorMessage(session, "메시지 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private void handleLocationUpdate(WebSocketSession session, Map<String, Object> messageData) {
        try {
            // 데이터 추출 및 검증
            String busNumber = (String) messageData.get("busNumber");
            String organizationId = (String) messageData.get("organizationId");
            Double latitude = ((Number) messageData.get("latitude")).doubleValue();
            Double longitude = ((Number) messageData.get("longitude")).doubleValue();
            Integer occupiedSeats = ((Number) messageData.get("occupiedSeats")).intValue();
            Long timestamp = ((Number) messageData.get("timestamp")).longValue();

            // 기본 검증
            if (busNumber == null || organizationId == null ||
                    latitude == null || longitude == null || occupiedSeats == null) {
                throw new IllegalArgumentException("필수 필드가 누락되었습니다");
            }

            // 위치 유효성 검증
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("유효하지 않은 GPS 좌표입니다");
            }

            // DTO 생성
            BusRealTimeLocationDTO locationUpdate = new BusRealTimeLocationDTO(
                    busNumber, organizationId, latitude, longitude, occupiedSeats, timestamp
            );

            // 세션 맵핑 등록 (처음 메시지를 보낼 때)
            if (!sessionToBusMap.containsKey(session.getId())) {
                sessionToBusMap.put(session.getId(), busNumber);
                driverSessions.put(busNumber, session);
                log.info("버스 기사 세션 등록: 버스 번호 = {}, 세션 ID = {}", busNumber, session.getId());
            }

            // 위치 업데이트 처리 (비동기)
            busService.updateBusLocation(locationUpdate);

            // 성공 응답
            sendSuccessMessage(session, "위치 업데이트가 성공적으로 처리되었습니다.");

        } catch (Exception e) {
            log.error("위치 업데이트 처리 실패: {}", e.getMessage());
            sendErrorMessage(session, "위치 업데이트 처리 실패: " + e.getMessage());
        }
    }

    private void handleLegacyLocationUpdate(WebSocketSession session, String payload) throws Exception {
        // 기존 로직 유지 (하위 호환성)
        BusRealTimeLocationDTO locationUpdate = objectMapper.readValue(payload, BusRealTimeLocationDTO.class);
        String busNumber = locationUpdate.getBusNumber();

        // 세션 맵핑 등록 (처음 메시지를 보낼 때)
        if (!sessionToBusMap.containsKey(session.getId())) {
            sessionToBusMap.put(session.getId(), busNumber);
            driverSessions.put(busNumber, session);
            log.info("버스 기사 세션 등록: 버스 번호 = {}, 세션 ID = {}", busNumber, session.getId());
        }

        // 위치 업데이트 처리
        busService.updateBusLocation(locationUpdate);

        // 성공 응답
        sendSuccessMessage(session, "위치 업데이트가 성공적으로 처리되었습니다.");
    }

    private void handleHeartbeat(WebSocketSession session) {
        try {
            sendMessage(session, Map.of(
                    "type", "heartbeat_response",
                    "status", "alive",
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("하트비트 응답 전송 실패: {}", e.getMessage());
        }
    }

    private void handleBusStatusUpdate(WebSocketSession session, Map<String, Object> messageData) {
        // 향후 확장을 위한 메서드
        log.debug("버스 상태 업데이트 수신: {}", messageData);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String busNumber = sessionToBusMap.get(session.getId());
        log.error("버스 기사 WebSocket 통신 오류: 세션 ID = {}, 버스 번호 = {}, 오류 = {}",
                session.getId(), busNumber, exception.getMessage(), exception);

        // 오류 발생 시 세션 정리
        try {
            session.close();
        } catch (Exception e) {
            log.error("세션 강제 종료 실패: {}", e.getMessage());
        }
    }

    /**
     * 특정 버스 기사에게 메시지 전송
     */
    public void sendMessageToBusDriver(String busNumber, Object message) {
        WebSocketSession session = driverSessions.get(busNumber);
        if (session != null && session.isOpen()) {
            try {
                sendMessage(session, message);
            } catch (Exception e) {
                log.error("버스 기사({})에게 메시지 전송 중 오류 발생: {}", busNumber, e.getMessage());

                // 세션이 유효하지 않으면 정리
                cleanupSession(session.getId(), busNumber);
            }
        } else {
            log.warn("버스 기사({})의 세션이 유효하지 않습니다", busNumber);
            if (session != null) {
                cleanupSession(session.getId(), busNumber);
            }
        }
    }

    /**
     * 활성화된 버스 기사 수 조회
     */
    public int getActiveBusDriverCount() {
        // 유효하지 않은 세션 제거 후 반환
        cleanupInvalidSessions();
        return driverSessions.size();
    }

    /**
     * 활성화된 버스 번호 목록 조회
     */
    public Set<String> getActiveBusNumbers() {
        cleanupInvalidSessions();
        return new HashSet<>(driverSessions.keySet());
    }

    private void sendSuccessMessage(WebSocketSession session, String message) {
        try {
            Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", message,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
        } catch (Exception e) {
            log.error("성공 메시지 전송 중 오류 발생", e);
        }
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> response = Map.of(
                    "status", "error",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
        } catch (Exception e) {
            log.error("오류 메시지 전송 중 오류 발생", e);
        }
    }

    /**
     * 세션에 메시지 전송 (내부 헬퍼 메서드)
     */
    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (session != null && session.isOpen()) {
            String jsonMessage = objectMapper.writeValueAsString(message);
            synchronized (session) { // 동시성 문제 방지
                session.sendMessage(new TextMessage(jsonMessage));
            }
        }
    }

    /**
     * 하트비트 체크 - 비활성 연결 감지 및 정리
     */
    private void checkHeartbeats() {
        Instant threshold = Instant.now().minusSeconds(300); // 5분 임계값

        lastHeartbeatMap.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            Instant lastHeartbeat = entry.getValue();

            if (lastHeartbeat.isBefore(threshold)) {
                log.warn("하트비트 타임아웃으로 세션 정리: 세션 ID = {}", sessionId);

                // 세션 정리
                String busNumber = sessionToBusMap.get(sessionId);
                cleanupSession(sessionId, busNumber);

                return true; // 맵에서 제거
            }

            return false;
        });
    }

    /**
     * 유효하지 않은 세션들 정리
     */
    private void cleanupInvalidSessions() {
        driverSessions.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) {
                String busNumber = entry.getKey();
                log.info("유효하지 않은 세션 정리: 버스 번호 = {}", busNumber);

                // 관련 맵에서도 제거
                sessionToBusMap.values().removeIf(bn -> bn.equals(busNumber));
                lastHeartbeatMap.keySet().removeIf(sid ->
                        busNumber.equals(sessionToBusMap.get(sid)));

                return true;
            }
            return false;
        });
    }

    /**
     * 오래된 세션들 정리 (가비지 컬렉션)
     */
    private void cleanupStaleSessions() {
        int beforeSize = driverSessions.size();
        cleanupInvalidSessions();
        int afterSize = driverSessions.size();

        if (beforeSize != afterSize) {
            log.info("세션 정리 완료: {} -> {} ({}개 정리)", beforeSize, afterSize, beforeSize - afterSize);
        }
    }

    /**
     * 특정 세션 정리
     */
    private void cleanupSession(String sessionId, String busNumber) {
        if (busNumber != null) {
            driverSessions.remove(busNumber);
        }
        sessionToBusMap.remove(sessionId);
        lastHeartbeatMap.remove(sessionId);
    }

    /**
     * 셧다운 훅 - 애플리케이션 종료 시 리소스 정리
     */
    @PreDestroy
    public void shutdown() {
        log.info("BusDriverWebSocketHandler 종료 중...");

        // 모든 세션 정리
        driverSessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.GOING_AWAY);
                }
            } catch (Exception e) {
                log.error("세션 종료 중 오류: {}", e.getMessage());
            }
        });

        // 스케줄러 종료
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("BusDriverWebSocketHandler 종료 완료");
    }
}