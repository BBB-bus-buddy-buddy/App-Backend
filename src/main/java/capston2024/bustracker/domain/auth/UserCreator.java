package capston2024.bustracker.domain.auth;

import capston2024.bustracker.config.status.Role;

/**
 *  처음 가입 시 엔티티 부여
 */
public class UserCreator {
    public static User createUserFrom(OAuthAttributes attributes) {
        return User.builder()
                .name(attributes.getName())
                .email(attributes.getEmail())
                .picture(attributes.getPicture())
                .role(Role.GUEST)
                .build();
    }
}