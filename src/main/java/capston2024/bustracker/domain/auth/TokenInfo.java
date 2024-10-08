package capston2024.bustracker.domain.auth;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo {
    @Id
    private String id;
    private String username;
    private String refreshToken;
    private String accessToken;

    public TokenInfo(String username, String refreshToken, String accessToken) {
        this.username = username;
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
    }
}