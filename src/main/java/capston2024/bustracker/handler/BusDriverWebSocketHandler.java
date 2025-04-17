package capston2024.bustracker.handler;

import capston2024.bustracker.config.dto.BusLocationUpdateDTO;
import capston2024.bustracker.service.BusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 버스 기사 앱과의 WebSocket 통신을 처리하는 핸들러
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BusDriverWebSocketHandler extends TextWebSocketHandler {

    private final BusService busService;
    private final ObjectMapper objectMapper;

    // 세션 관리를 위한 맵 (busNumber -> WebSocketSession)
    private final Map<String, WebSocketSession> driverSessions = new ConcurrentHashMap<>();
    // 세션 역매핑을 위한 맵 (sessionId -> busNumber)
    private final Map<String, String> sessionToBusMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("버스 기사 WebSocket 연결 설정: {}", session.getId());
        // 이 시점에는 아직 어떤 버스 기사인지 모르므로, 메시지를 받을 때 맵핑함
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String busNumber = sessionToBusMap.remove(sessionId);

        if (busNumber != null) {
            driverSessions.remove(busNumber);
            log.info("버스 기사 WebSocket 연결 종료: 세션 ID = {}, 버스 번호 = {}", sessionId, busNumber);
        } else {
            log.info("버스 기사 WebSocket 연결 종료: 세션 ID = {}", sessionId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("버스 기사로부터 메시지 수신: {}", payload);

        try {
            BusLocationUpdateDTO locationUpdate = objectMapper.readValue(payload, BusLocationUpdateDTO.class);
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
            sendMessage(session, Map.of(
                    "status", "success",
                    "message", "위치 업데이트가 성공적으로 처리되었습니다.",
                    "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("버스 위치 업데이트 처리 중 오류 발생", e);
            sendMessage(session, Map.of(
                    "status", "error",
                    "message", "위치 업데이트 처리 중 오류가 발생했습니다: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("버스 기사 WebSocket 통신 오류: 세션 ID = {}", session.getId(), exception);
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
                log.error("버스 기사({})에게 메시지 전송 중 오류 발생", busNumber, e);
            }
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