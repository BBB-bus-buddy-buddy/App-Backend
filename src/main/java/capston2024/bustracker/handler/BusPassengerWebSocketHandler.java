package capston2024.bustracker.handler;

import capston2024.bustracker.config.dto.BusBoardingDTO;
import capston2024.bustracker.config.dto.BusRealTimeStatusDTO;
import capston2024.bustracker.config.dto.PassengerLocationDTO;
import capston2024.bustracker.service.BusService;
import capston2024.bustracker.service.PassengerLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 승객 앱과의 WebSocket 통신을 처리하는 핸들러
 */
@Component
@Slf4j
public class BusPassengerWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    // ApplicationContext를 사용하여 지연 초기화
    private final ApplicationContext applicationContext;

    // 조직별 승객 세션 관리 (organizationId -> Set<WebSocketSession>)
    private final Map<String, Set<WebSocketSession>> organizationSessions = new ConcurrentHashMap<>();
    // 세션 역매핑을 위한 맵 (sessionId -> organizationId)
    private final Map<String, String> sessionToOrgMap = new ConcurrentHashMap<>();
    // 세션과 사용자 ID 매핑 (sessionId -> userId)
    private final Map<String, String> sessionToUserMap = new ConcurrentHashMap<>();

    @Autowired
    public BusPassengerWebSocketHandler(ObjectMapper objectMapper, ApplicationContext applicationContext) {
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
    }

    // 지연 초기화를 통해 BusService 얻기
    private BusService getBusService() {
        return applicationContext.getBean(BusService.class);
    }

    // 지연 초기화를 통해 PassengerLocationService 얻기
    private PassengerLocationService getPassengerLocationService() {
        return applicationContext.getBean(PassengerLocationService.class);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("승객 WebSocket 연결 설정: {}", session.getId());
        // 이 시점에는 아직 어떤 조직인지 모르므로, 메시지를 받을 때 맵핑함
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
                log.info("승객 WebSocket 연결 종료: 세션 ID = {}, 조직 ID = {}, 사용자 ID = {}",
                        sessionId, organizationId, userId);
            }
        } else {
            log.info("승객 WebSocket 연결 종료: 세션 ID = {}", sessionId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("승객으로부터 메시지 수신: {}", payload);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String messageType = (String) data.get("type");
            String organizationId = (String) data.get("organizationId");

            if (organizationId == null || organizationId.isEmpty()) {
                sendErrorMessage(session, "조직 ID가 필요합니다.");
                return;
            }

            // 세션 맵핑 등록 (처음 메시지를 보낼 때)
            if (!sessionToOrgMap.containsKey(session.getId())) {
                sessionToOrgMap.put(session.getId(), organizationId);
                organizationSessions.computeIfAbsent(organizationId, k -> ConcurrentHashMap.newKeySet())
                        .add(session);
                log.info("승객 세션 등록: 조직 ID = {}, 세션 ID = {}", organizationId, session.getId());
            }

            // 메시지 타입에 따른 처리
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
                default:
                    sendErrorMessage(session, "알 수 없는 메시지 타입: " + messageType);
            }

        } catch (Exception e) {
            log.error("승객 메시지 처리 중 오류 발생", e);
            sendErrorMessage(session, "메시지 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 승객 위치 메시지 처리
     */
    private void handleLocationMessage(WebSocketSession session, Map<String, Object> data) {
        try {
            Map<String, Object> locationData = (Map<String, Object>) data.get("data");

            PassengerLocationDTO locationDTO = new PassengerLocationDTO();
            locationDTO.setUserId((String) locationData.get("userId"));
            locationDTO.setOrganizationId((String) data.get("organizationId"));
            locationDTO.setLatitude((Double) locationData.get("latitude"));
            locationDTO.setLongitude((Double) locationData.get("longitude"));
            locationDTO.setTimestamp(System.currentTimeMillis());

            // 사용자 ID 저장
            if (locationDTO.getUserId() != null) {
                sessionToUserMap.put(session.getId(), locationDTO.getUserId());
            }

            // 위치 처리 서비스 호출
            boolean boardingDetected = getPassengerLocationService().processPassengerLocation(locationDTO);

            if (boardingDetected) {
                // 자동 탑승 감지 시 클라이언트에 알림
                sendSuccessMessage(session, "버스 탑승이 자동으로 감지되었습니다.");
            }

        } catch (Exception e) {
            log.error("위치 메시지 처리 중 오류 발생", e);
            sendErrorMessage(session, "위치 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("승객 WebSocket 통신 오류: 세션 ID = {}", session.getId(), exception);
    }

    /**
     * 특정 조직의 모든 승객에게 버스 상태 업데이트 전송
     */
    public void broadcastBusStatus(String organizationId, BusRealTimeStatusDTO busStatus) {
        Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
        if (sessions != null && !sessions.isEmpty()) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        Map<String, Object> message = Map.of(
                                "type", "busUpdate",
                                "data", busStatus
                        );
                        sendMessage(session, message);
                    } catch (Exception e) {
                        log.error("승객에게 버스 상태 업데이트 전송 중 오류 발생: 세션 ID = {}", session.getId(), e);
                    }
                }
            }
            log.debug("조직 {}의 {}명의 승객에게 버스 상태 업데이트 전송", organizationId, sessions.size());
        }
    }

    /**
     * 구독 메시지 처리 (특정 버스 또는 노선 구독)
     */
    private void handleSubscribeMessage(WebSocketSession session, Map<String, Object> data) {
        // 구독 처리 로직
        // 이 구현에서는 모든 승객이 조직의 모든 버스 업데이트를 받도록 함
        String organizationId = (String) data.get("organizationId");

        // 해당 조직의 모든 버스 상태 즉시 전송
        getBusService().getAllBusStatusByOrganizationId(organizationId).forEach(busStatus -> {
            try {
                Map<String, Object> message = Map.of(
                        "type", "busUpdate",
                        "data", busStatus
                );
                sendMessage(session, message);
            } catch (Exception e) {
                log.error("초기 버스 상태 전송 중 오류 발생", e);
            }
        });

        sendSuccessMessage(session, "구독이 성공적으로 등록되었습니다.");
    }

    /**
     * 승객 탑승/하차 메시지 처리
     */
    private void handleBoardingMessage(WebSocketSession session, Map<String, Object> data) {
        try {
            Map<String, Object> boardingData = (Map<String, Object>) data.get("data");

            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber((String) boardingData.get("busNumber"));
            boardingDTO.setOrganizationId((String) data.get("organizationId"));
            boardingDTO.setUserId((String) boardingData.get("userId"));
            boardingDTO.setAction(BusBoardingDTO.BoardingAction.valueOf((String) boardingData.get("action")));
            boardingDTO.setTimestamp(System.currentTimeMillis());

            boolean success = getBusService().processBusBoarding(boardingDTO);

            if (success) {
                sendSuccessMessage(session, boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD ?
                        "탑승이 성공적으로 처리되었습니다." : "하차가 성공적으로 처리되었습니다.");
            } else {
                sendErrorMessage(session, "탑승/하차 처리에 실패했습니다.");
            }
        } catch (Exception e) {
            log.error("탑승/하차 메시지 처리 중 오류 발생", e);
            sendErrorMessage(session, "탑승/하차 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 성공 메시지 전송
     */
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

    /**
     * 오류 메시지 전송
     */
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
        String jsonMessage = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(jsonMessage));
    }
}