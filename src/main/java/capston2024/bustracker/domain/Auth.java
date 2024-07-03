package capston2024.bustracker.domain;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


@Document(collection = "auth")
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

    public String getSchoolCode() {
        return schoolCode;
    }

    public void setSchoolCode(String schoolCode) {
        this.schoolCode = schoolCode;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
