package capston2024.bustracker.domain;
import capston2024.bustracker.config.status.Role;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.minidev.json.annotate.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 비지니스 도메인 객체, ex) 회원, 주문, 쿠폰 등등 주로 DB에 저장하고 관리되는 것들
 */


@Setter
@Getter
@Document(collection = "User")
@NoArgsConstructor
public class User {

    @Builder
    public User(String name, String email, String picture, Role role){
        this.name = name;
        this.email = email;
        this.picture = picture;
        this.role = role;
    }

    public User update(String name, String picture){
        this.name = name;
        this.picture = picture;

        return this;
    }

    public enum AuthProvider {
        google
    }
    @Id
    private String id;
    @NotNull
    private String name; // 이름
    @NotNull
    private String email; // 이메일
    private String picture;
    private Role role;

    @JsonIgnore
    private String password;

    private AuthProvider provider; // google
    private String providerId;

    private String schoolCode; // 인증된 학교 코드
    private boolean isValid = false; // 검증상태 여부

    public String getRoleKey(){
        return this.role.getKey();
    }
}
