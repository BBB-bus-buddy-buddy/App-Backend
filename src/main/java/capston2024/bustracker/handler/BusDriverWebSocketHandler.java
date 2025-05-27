package capston2024.bustracker.handler;

import capston2024.bustracker.config.dto.busEtc.DriverLocationUpdateDTO;
import capston2024.bustracker.service.RealtimeLocationService;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class BusDriverWebSocketHandler extends TextWebSocketHandler {

    private final RealtimeLocationService realtimeLocationService;
    private final ObjectMapper objectMapper;

    // 세션 관리 (operationId -> WebSocketSession)
    private final Map<String, WebSocketSession> driverSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToOperationMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("기사 WebSocket 연결: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String operationId = sessionToOperationMap.remove(sessionId);

        if (operationId != null) {
            driverSessions.remove(operationId);
            log.info("기사 WebSocket 연결 종료: 세션={}, 운행={}", sessionId, operationId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("기사 위치 데이터 수신: {}", payload);

        try {
            // JSON → DriverLocationUpdateDTO 변환
            DriverLocationUpdateDTO locationUpdate = objectMapper.readValue(payload, DriverLocationUpdateDTO.class);
            String operationId = locationUpdate.getOperationId();

            // 세션 등록 (최초 연결 시)
            registerDriverSession(session, operationId);

            // 실시간 위치 업데이트 처리
            boolean success = realtimeLocationService.updateDriverLocation(locationUpdate);

            // 기사 앱에 응답 전송
            sendResponse(session, success, success ? "위치 업데이트 완료" : "위치 업데이트 실패");

        } catch (Exception e) {
            log.error("기사 위치 업데이트 처리 중 오류", e);
            sendResponse(session, false, "처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 기사 세션 등록
     */
    private void registerDriverSession(WebSocketSession session, String operationId) {
        if (!sessionToOperationMap.containsKey(session.getId())) {
            sessionToOperationMap.put(session.getId(), operationId);
            driverSessions.put(operationId, session);
            log.info("기사 세션 등록: 운행={}, 세션={}", operationId, session.getId());
        }
    }

    /**
     * 기사 앱에 응답 전송
     */
    private void sendResponse(WebSocketSession session, boolean success, String message) {
        try {
            Map<String, Object> response = Map.of(
                    "status", success ? "success" : "error",
                    "message", message,
                    "timestamp", System.currentTimeMillis()
            );

            String jsonResponse = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(jsonResponse));

        } catch (IOException e) {
            log.error("기사 앱 응답 전송 실패", e);
        }
    }

    /**
     * 특정 운행의 기사에게 메시지 전송 (서버에서 기사에게)
     */
    public void sendMessageToDriver(String operationId, Object message) {
        WebSocketSession session = driverSessions.get(operationId);
        if (session != null && session.isOpen()) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (Exception e) {
                log.error("기사({})에게 메시지 전송 실패", operationId, e);
            }
        }
    }
}