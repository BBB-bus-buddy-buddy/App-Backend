package capston2024.bustracker.handler;

import capston2024.bustracker.domain.BusCoordinate;
import capston2024.bustracker.repository.BusCoordinateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;

/**
 * 실시간 버스 위치정보를 받아오는 아두이노 웹소켓 핸들러
 */
@Component
public class BusCoordinateWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    @Autowired
    private BusCoordinateRepository repository;

    public BusCoordinateWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        BusCoordinate location = objectMapper.readValue(message.getPayload(), BusCoordinate.class);
        location.setTimestamp(Instant.now());
        repository.save(location);
    }

}