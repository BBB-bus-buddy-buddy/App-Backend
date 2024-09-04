package capston2024.bustracker.domain;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.minidev.json.annotate.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 비지니스 도메인 객체, ex) 회원, 주문, 쿠폰 등등 주로 DB에 저장하고 관리되는 것들
 */


@Setter
@Getter
@Document(collection = "Auth")
public class Auth {
    public enum AuthProvider {
        google
    }
    @Id
    private String id;
    @NotNull
    private String username; // 이름
    @NotNull
    private String email; // 이메일
    private String imgUrl;
    private Role role;

    @JsonIgnore
    private String password;

    private AuthProvider provider; // google
    private String providerId;

    private String schoolCode; // 인증된 학교 코드
    private boolean isValid = false; // 검증상태 여부
}
