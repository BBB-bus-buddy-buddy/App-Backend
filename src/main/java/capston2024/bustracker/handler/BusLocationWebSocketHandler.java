package capston2024.bustracker.handler;

import capston2024.bustracker.config.dto.LocationDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.repository.BusRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;

/**
 * 실시간 버스 위치정보를 받아오는 아두이노 웹소켓 핸들러
 */
@Component
public class BusLocationWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    @Autowired
    private BusRepository repository;

    public BusLocationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        LocationDTO locationDTO = objectMapper.readValue(message.getPayload(), LocationDTO.class);
        locationDTO.setTimestamp(Instant.now());
        // 형변환: LocationDTO -> Bus
        Bus bus = new Bus();
        bus.setSeat(locationDTO.getSeat());
        bus.setLocation(new GeoJsonPoint(locationDTO.getLongitude(), locationDTO.getLatitude()));
        bus.setTimestamp(locationDTO.getTimestamp());
        repository.save(bus);
    }

}