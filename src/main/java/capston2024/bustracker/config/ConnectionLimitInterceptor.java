// 2. 연결 제한 인터셉터 추가
package capston2024.bustracker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ConnectionLimitInterceptor implements HandshakeInterceptor {

    private static final int MAX_CONNECTIONS_PER_IP = 100; // IP당 최대 연결 수 (대규모 동시 접속 대응)
    private static final Map<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        String clientIp = getClientIp(request);

        // IP별 연결 수 체크
        AtomicInteger count = connectionCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));

        if (count.get() >= MAX_CONNECTIONS_PER_IP) {
            log.warn("Connection limit exceeded for IP: {} (current: {})", clientIp, count.get());
            return false; // 연결 거부
        }

        count.incrementAndGet();
        attributes.put("CLIENT_IP", clientIp);
        log.info("WebSocket connection accepted for IP: {} (connections: {})", clientIp, count.get());

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 핸드셰이크 실패 시 카운트 감소
        if (exception != null) {
            String clientIp = getClientIp(request);
            decrementConnection(clientIp);
            log.warn("Handshake failed for IP: {}, connection count decremented. Error: {}",
                    clientIp, exception.getMessage());
        }
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    // 연결 종료 시 카운트 감소
    public static void decrementConnection(String clientIp) {
        AtomicInteger count = connectionCounts.get(clientIp);
        if (count != null) {
            int newCount = count.decrementAndGet();
            if (newCount <= 0) {
                connectionCounts.remove(clientIp);
            }
        }
    }
}