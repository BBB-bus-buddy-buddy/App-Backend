package capston2024.bustracker.handler;

import capston2024.bustracker.config.dto.busEtc.BusBoardingDTO;
import capston2024.bustracker.config.dto.busEtc.PassengerLocationDTO;
import capston2024.bustracker.config.dto.realtime.BoardingDetectionResultDTO;
import capston2024.bustracker.service.RealtimeLocationService;
import capston2024.bustracker.service.PassengerLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class BusPassengerWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final PassengerLocationService passengerLocationService;

    // 조직별 승객 세션 관리
    private final Map<String, Set<WebSocketSession>> organizationSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToOrgMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUserMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("승객 WebSocket 연결: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String organizationId = sessionToOrgMap.remove(sessionId);
        String userId = sessionToUserMap.remove(sessionId);

        if (organizationId != null) {
            Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
            if (sessions != null) {
                sessions.remove(session);
            }
            log.info("승객 WebSocket 연결 종료: 세션={}, 조직={}, 사용자={}", sessionId, organizationId, userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("승객 메시지 수신: {}", payload);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String messageType = (String) data.get("type");
            String organizationId = (String) data.get("organizationId");

            // 세션 등록
            registerPassengerSession(session, organizationId);

            // 메시지 타입별 처리
            switch (messageType) {
                case "subscribe" -> handleSubscribe(session, data);
                case "location" -> handlePassengerLocation(session, data);
                case "manualBoarding" -> handleManualBoarding(session, data);
                default -> sendError(session, "알 수 없는 메시지 타입: " + messageType);
            }

        } catch (Exception e) {
            log.error("승객 메시지 처리 중 오류", e);
            sendError(session, "메시지 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 승객 위치 기반 자동 탑승 감지 처리
     */
    private void handlePassengerLocation(WebSocketSession session, Map<String, Object> data) {
        try {
            Map<String, Object> locationData = (Map<String, Object>) data.get("data");

            PassengerLocationDTO locationDTO = new PassengerLocationDTO();
            locationDTO.setUserId((String) locationData.get("userId"));
            locationDTO.setOrganizationId((String) data.get("organizationId"));
            locationDTO.setLatitude((Double) locationData.get("latitude"));
            locationDTO.setLongitude((Double) locationData.get("longitude"));
            locationDTO.setTimestamp(System.currentTimeMillis());

            // 사용자 ID 세션에 저장
            sessionToUserMap.put(session.getId(), locationDTO.getUserId());

            // 자동 탑승 감지 처리
            BoardingDetectionResultDTO result = passengerLocationService.processPassengerLocation(locationDTO);

            if (result != null && result.isAutoDetected()) {
                // 자동 탑승/하차 감지 시 승객에게 알림
                Map<String, Object> notification = Map.of(
                        "type", "boardingDetected",
                        "data", result
                );
                sendMessage(session, notification);
            }

        } catch (Exception e) {
            log.error("승객 위치 처리 중 오류", e);
            sendError(session, "위치 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 수동 탑승/하차 처리
     */
    private void handleManualBoarding(WebSocketSession session, Map<String, Object> data) {
        try {
            Map<String, Object> boardingData = (Map<String, Object>) data.get("data");

            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber((String) boardingData.get("busNumber"));
            boardingDTO.setOrganizationId((String) data.get("organizationId"));
            boardingDTO.setUserId((String) boardingData.get("userId"));
            boardingDTO.setAction(BusBoardingDTO.BoardingAction.valueOf((String) boardingData.get("action")));
            boardingDTO.setTimestamp(System.currentTimeMillis());

            // 수동 탑승/하차 처리 (BusOperationService 통해)
            boolean success = passengerLocationService.processManualBoarding(boardingDTO);

            Map<String, Object> response = Map.of(
                    "type", "boardingResult",
                    "success", success,
                    "message", success ? "탑승/하차 처리 완료" : "탑승/하차 처리 실패",
                    "action", boardingDTO.getAction()
            );
            sendMessage(session, response);

        } catch (Exception e) {
            log.error("수동 탑승 처리 중 오류", e);
            sendError(session, "탑승 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 실시간 버스 상태 브로드캐스트 (이벤트 리스너)
     */
    @EventListener
    public void handlePassengerBroadcast(RealtimeLocationService.PassengerBroadcastEvent event) {
        Set<WebSocketSession> sessions = organizationSessions.get(event.organizationId());

        if (sessions != null && !sessions.isEmpty()) {
            Map<String, Object> message = Map.of(
                    "type", "busUpdate",
                    "data", event.busStatus()
            );

            // 해당 조직의 모든 승객에게 브로드캐스트
            sessions.forEach(session -> {
                if (session.isOpen()) {
                    try {
                        sendMessage(session, message);
                    } catch (Exception e) {
                        log.error("승객 브로드캐스트 실패: 세션={}", session.getId(), e);
                    }
                }
            });

            log.debug("조직 {}의 {}명 승객에게 버스 상태 브로드캐스트",
                    event.organizationId(), sessions.size());
        }
    }

    /**
     * 승객 세션 등록
     */
    private void registerPassengerSession(WebSocketSession session, String organizationId) {
        if (!sessionToOrgMap.containsKey(session.getId())) {
            sessionToOrgMap.put(session.getId(), organizationId);
            organizationSessions.computeIfAbsent(organizationId, k -> ConcurrentHashMap.newKeySet())
                    .add(session);
            log.info("승객 세션 등록: 조직={}, 세션={}", organizationId, session.getId());
        }
    }

    /**
     * 구독 처리 (초기 버스 상태 전송)
     */
    private void handleSubscribe(WebSocketSession session, Map<String, Object> data) {
        try {
            String organizationId = (String) data.get("organizationId");
            // 해당 조직의 현재 운행 중인 버스들의 상태를 즉시 전송
            // (RealtimeLocationService를 통해 현재 상태 조회 후 전송)

            Map<String, Object> response = Map.of(
                    "type", "subscribed",
                    "message", "실시간 버스 정보 구독 완료"
            );
            sendMessage(session, response);

        } catch (Exception e) {
            log.error("구독 처리 중 오류", e);
            sendError(session, "구독 처리 중 오류: " + e.getMessage());
        }
    }

    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        String jsonMessage = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(jsonMessage));
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> response = Map.of(
                    "type", "error",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
        } catch (Exception e) {
            log.error("에러 메시지 전송 실패", e);
        }
    }
}