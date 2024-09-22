package capston2024.bustracker.handler;

import capston2024.bustracker.config.dto.LocationDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.service.BusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;

/**
 * 실시간 버스 위치정보를 받아오는 아두이노 웹소켓 핸들러
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusLocationWebSocketCsvHandler extends TextWebSocketHandler {

    private final BusService busLocationService;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String csvData = message.getPayload();
        busLocationService.processBusLocationAsync(csvData)
                .thenAccept(bus -> {
                    try {
                        session.sendMessage(new TextMessage("Location processed: " + bus.getId()));
                    } catch (IOException e) {
                        handleException(session, "Error sending confirmation", e);
                    }
                })
                .exceptionally(ex -> {
                    handleException(session, "Error processing bus location", ex);
                    return null;
                });
    }

    private void handleException(WebSocketSession session, String message, Throwable ex) {
        try {
            session.sendMessage(new TextMessage("Error: " + message));
        } catch (IOException e) {
            // 로그에 에러 기록
            ex.printStackTrace();
        }
        // 로그에 원래 예외 기록
        ex.printStackTrace();
    }
}