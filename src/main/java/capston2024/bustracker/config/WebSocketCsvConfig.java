package capston2024.bustracker.config;

import capston2024.bustracker.handler.BusLocationWebSocketCsvHandler;
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
                .setAllowedOriginPatterns("*");  // allowedOrigins 대신 allowedOriginPatterns 사용
    }
}
