package capston2024.bustracker.domain.auth;
import capston2024.bustracker.config.status.Role;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 비지니스 도메인 객체, ex) 회원, 주문, 쿠폰 등등 주로 DB에 저장하고 관리되는 것들
 */


@Setter @Getter
@Document(collection = "Auth")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {
    @Id
    private String id;

    @NotNull
    private String name; // 이름
    @NotNull
    private String email; // 이메일
    private String picture;
    private Role role;
    private String organizationId; // 기관 인증 Id

    public void updateRole(Role role){
        this.role = role;
    }

    public String getRoleKey() {
        return this.role.getKey();
    }
}
