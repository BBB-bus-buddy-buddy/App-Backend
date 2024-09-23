package capston2024.bustracker.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class ApiKeyConfig {

    @Getter
    private static String univApiKey;

    @Value("${UNIV_API_KEY}")
    public void setUnivApiKey(String key) {
        univApiKey = key;
    }
}