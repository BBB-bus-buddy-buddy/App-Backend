package capston2024.bustracker.domain;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Setter
@Getter
@Document(collection = "Auth")
public class Auth {
    @Id
    private Long id; // 구글로 로그인 한 유저의 고유 id
    private String provider; // google
    @NotNull
    private String email; // 실제 id
    @NotNull
    private String name; // 이름
    private String picture;

    private String schoolCode; // 인증된 학교 코드
    private boolean isValid; // 검증상태 여부

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

}
