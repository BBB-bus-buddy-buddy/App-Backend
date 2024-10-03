package capston2024.bustracker.WebSocketClient;

import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.handler.BusLocationWebSocketCsvHandler;
import capston2024.bustracker.service.BusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusLocationWebSocketCsvHandlerTest {

    @Mock
    private BusService busService;

    @Mock
    private WebSocketSession webSocketSession;

    private BusLocationWebSocketCsvHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BusLocationWebSocketCsvHandler(busService);
    }

    @Test
    void handleTextMessage_SendMessageError() throws Exception {
        // Given
        String csvData = "37.5665,126.9780";
        TextMessage message = new TextMessage(csvData);
        Bus mockBus = new Bus();
        mockBus.setId("testBusId");

        when(busService.processBusLocationAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(mockBus));
        doThrow(new IOException("Send error")).when(webSocketSession).sendMessage(any(TextMessage.class));

        // When
        handler.handleTextMessage(webSocketSession, message);

        // Then
        verify(busService).processBusLocationAsync(csvData);
        verify(webSocketSession, times(1)).sendMessage(any(TextMessage.class));
        // 로그 검증이 필요한 경우 여기에 추가
    }
}