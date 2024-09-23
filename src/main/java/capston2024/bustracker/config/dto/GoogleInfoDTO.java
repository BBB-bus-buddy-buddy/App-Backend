package capston2024.bustracker.config.dto;

import capston2024.bustracker.domain.auth.User;
import lombok.Getter;

/**
 * 인증된 사용자 정보를 모아 놓는 클래스
 */
@Getter
public class GoogleInfoDTO {
    private String name;
    private String email;
    private String picture;

    public GoogleInfoDTO(User user) {
        this.name = user.getName();
        this.email = user.getEmail();
        this.picture = user.getPicture();
    }
}