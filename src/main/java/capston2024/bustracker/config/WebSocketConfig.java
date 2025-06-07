package capston2024.bustracker.config;

import capston2024.bustracker.handler.BusDriverWebSocketHandler;
import capston2024.bustracker.handler.BusPassengerWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {


    @Autowired
    private BusDriverWebSocketHandler busDriverWebSocketHandler;

    @Autowired
    private BusPassengerWebSocketHandler busPassengerWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 버스 기사용 웹소켓 엔드포인트 - 보안 강화
        registry.addHandler(busDriverWebSocketHandler, "/ws/driver")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new ConnectionLimitInterceptor()); // 연결 제한 추가

        // 승객용 웹소켓 엔드포인트 - 보안 강화
        registry.addHandler(busPassengerWebSocketHandler, "/ws/passenger")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new ConnectionLimitInterceptor()); // 연결 제한 추가
    }
}