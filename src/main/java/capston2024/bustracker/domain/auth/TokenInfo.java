package capston2024.bustracker.domain.auth;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

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
    @Indexed(expireAfterSeconds = 0)  // TTL 인덱스 설정
    private Date expirationDate;  // 만료 시간 필드 추가

    public TokenInfo(String username, String refreshToken, String accessToken, Date expirationDate) {
        this.username = username;
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.expirationDate = expirationDate;
    }
}