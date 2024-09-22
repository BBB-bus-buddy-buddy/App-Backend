package capston2024.bustracker.handler;

import capston2024.bustracker.config.dto.LocationDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.repository.BusRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class BusLocationWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final BusRepository busRepository;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        LocationDTO locationDTO = parseLocationData(message);
        Bus bus = createBusFromLocationDTO(locationDTO);
        busRepository.save(bus);
    }

    private LocationDTO parseLocationData(TextMessage message) throws Exception {
        LocationDTO locationDTO = objectMapper.readValue(message.getPayload(), LocationDTO.class);
        locationDTO.setTimestamp(Instant.now());
        return locationDTO;
    }

    private Bus createBusFromLocationDTO(LocationDTO locationDTO) {
        return Bus.builder()
                .location(new GeoJsonPoint(locationDTO.getLongitude(), locationDTO.getLatitude()))
                .timestamp(locationDTO.getTimestamp())
                .build();
    }
}