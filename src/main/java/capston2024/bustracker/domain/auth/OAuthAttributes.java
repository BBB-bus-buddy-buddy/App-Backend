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
        if ("apple".equals(registrationId)) {
            return ofApple(attributes);
        }
        return ofGoogle(attributes);
    }

    public static OAuthAttributes ofGoogle(Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .name((String) attributes.get("name"))
                .email((String) attributes.get("email"))
                .attributes(attributes)
                .build();
    }

    public static OAuthAttributes ofApple(Map<String, Object> attributes) {
        // Apple의 경우 name이 복합 객체로 올 수 있음
        String name = null;
        String email = (String) attributes.get("email");

        // name이 없으면 email의 앞부분을 사용
        if (attributes.get("name") != null) {
            Object nameObj = attributes.get("name");
            if (nameObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nameMap = (Map<String, Object>) nameObj;
                String firstName = (String) nameMap.get("firstName");
                String lastName = (String) nameMap.get("lastName");
                name = (firstName != null && lastName != null)
                    ? lastName + firstName
                    : firstName != null ? firstName : lastName;
            } else {
                name = nameObj.toString();
            }
        }

        // name이 여전히 null이면 email에서 추출
        if (name == null && email != null) {
            name = email.split("@")[0];
        }

        return OAuthAttributes.builder()
                .name(name)
                .email(email)
                .attributes(attributes)
                .build();
    }
}