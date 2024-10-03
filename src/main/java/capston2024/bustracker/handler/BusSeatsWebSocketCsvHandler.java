package capston2024.bustracker.handler;

import capston2024.bustracker.service.BusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

@RequiredArgsConstructor
@Component
@Slf4j
public class BusSeatsWebSocketCsvHandler extends TextWebSocketHandler {
    private final BusService busService;

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String csvData = message.getPayload();
        busService.processBusSeatAsync(csvData)
                .thenAccept(bus -> {
                    try {
                        session.sendMessage(new TextMessage("occupied seat processed: " + bus.getId()));
                    } catch (IOException e) {
                        log.error("Error sending confirmation", e);
                    }
                })
                .exceptionally(ex -> {
                    handleException(session, "Error processing bus seats information", ex);
                    return null;
                });
    }

    private void handleException(WebSocketSession session, String message, Throwable ex) {
        log.error(message, ex);
        try {
            session.sendMessage(new TextMessage("Error: " + message));
        } catch (IOException e) {
            log.error("Failed to send error message to client", e);
        }
    }
  }
