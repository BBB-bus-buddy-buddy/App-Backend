package capston2024.bustracker.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    // 대규모 동시 접속 대응 설정 (상수값)
    private static final int MAX_POOL_SIZE = 200;              // 최대 연결 수
    private static final int MIN_POOL_SIZE = 20;               // 최소 연결 수
    private static final int MAX_CONNECTION_IDLE_TIME = 60000; // 유휴 연결 타임아웃 (60초)
    private static final int MAX_CONNECTION_LIFE_TIME = 300000;// 연결 최대 수명 (5분)
    private static final int CONNECT_TIMEOUT = 10000;          // 연결 타임아웃 (10초)
    private static final int SOCKET_TIMEOUT = 30000;           // 소켓 타임아웃 (30초)
    private static final int SERVER_SELECTION_TIMEOUT = 30000; // 서버 선택 타임아웃 (30초)

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    public MongoClient mongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToConnectionPoolSettings(builder -> builder
                        .maxSize(MAX_POOL_SIZE)
                        .minSize(MIN_POOL_SIZE)
                        .maxConnectionIdleTime(MAX_CONNECTION_IDLE_TIME, TimeUnit.MILLISECONDS)
                        .maxConnectionLifeTime(MAX_CONNECTION_LIFE_TIME, TimeUnit.MILLISECONDS)
                )
                .applyToSocketSettings(builder -> builder
                        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                        .readTimeout(SOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
                )
                .applyToClusterSettings(builder -> builder
                        .serverSelectionTimeout(SERVER_SELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                )
                .build();

        return MongoClients.create(settings);
    }
}