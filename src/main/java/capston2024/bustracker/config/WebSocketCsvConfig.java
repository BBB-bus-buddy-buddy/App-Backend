package capston2024.bustracker.config;

import capston2024.bustracker.handler.BusLocationWebSocketCsvHandler;
import capston2024.bustracker.handler.BusLocationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketCsvConfig implements WebSocketConfigurer {

    @Autowired
    private BusLocationWebSocketCsvHandler busLocationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(busLocationWebSocketHandler, "/bus-location")
                .setAllowedOrigins("*")
                .withSockJS();  // SockJS 지원 추가 (옵션)
    }
}
