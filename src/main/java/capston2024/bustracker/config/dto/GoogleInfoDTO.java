package capston2024.bustracker.config.dto;

import capston2024.bustracker.domain.User;
import lombok.Getter;

/**
 * 인증된 사용자 정보를 모아 놓는 클래스
 */
@Getter
public class GoogleInfoDTO {
    private String name;
    private String email;

    public GoogleInfoDTO(User user) {
        this.name = user.getName();
        this.email = user.getEmail();
    }
}