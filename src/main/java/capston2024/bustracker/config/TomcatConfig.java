package capston2024.bustracker.config;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat 서버 설정 - 대규모 동시 접속 대응
 */
@Configuration
public class TomcatConfig {

    // 대규모 동시 접속 대응 설정 (상수값)
    private static final int MAX_THREADS = 500;                 // 최대 스레드 수
    private static final int MIN_SPARE_THREADS = 50;            // 최소 스레드 수
    private static final int MAX_CONNECTIONS = 10000;           // 최대 연결 수
    private static final int ACCEPT_COUNT = 500;                // Accept 큐 크기
    private static final int CONNECTION_TIMEOUT = 30000;        // 연결 타임아웃 (30초)
    private static final int KEEP_ALIVE_TIMEOUT = 60000;        // Keep-Alive 타임아웃 (60초)
    private static final int MAX_KEEP_ALIVE_REQUESTS = 100;     // 최대 Keep-Alive 요청 수

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            factory.addConnectorCustomizers(connector -> {
                Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();

                // 스레드 풀 설정
                protocol.setMaxThreads(MAX_THREADS);
                protocol.setMinSpareThreads(MIN_SPARE_THREADS);

                // 연결 설정
                protocol.setMaxConnections(MAX_CONNECTIONS);
                protocol.setAcceptCount(ACCEPT_COUNT);
                protocol.setConnectionTimeout(CONNECTION_TIMEOUT);

                // Keep-Alive 설정
                protocol.setKeepAliveTimeout(KEEP_ALIVE_TIMEOUT);
                protocol.setMaxKeepAliveRequests(MAX_KEEP_ALIVE_REQUESTS);

                // HTTP/2 활성화
                connector.addUpgradeProtocol(new org.apache.coyote.http2.Http2Protocol());

                // 압축 설정
                protocol.setCompression("on");
                protocol.setCompressionMinSize(1024);
                protocol.setCompressibleMimeType(
                        "application/json,application/xml,text/html,text/xml,text/plain,application/javascript,text/css"
                );
            });
        };
    }
}
