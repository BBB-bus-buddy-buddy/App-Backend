package capston2024.bustracker.domain.auth;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;
@Getter
public class OAuthAttributes {
    private Map<String, Object> attributes;
    private String name;
    private String email;

    @Builder
    public OAuthAttributes(Map<String, Object> attributes, String name, String email) {
        this.attributes = attributes;
        this.name = name;
        this.email = email;
    }

    public static OAuthAttributes of(String registrationId, Map<String, Object> attributes) {
        return ofGoogle(attributes);
    }

    public static OAuthAttributes ofGoogle(Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .name((String) attributes.get("name"))
                .email((String) attributes.get("email"))
                .attributes(attributes)
                .build();
    }
}