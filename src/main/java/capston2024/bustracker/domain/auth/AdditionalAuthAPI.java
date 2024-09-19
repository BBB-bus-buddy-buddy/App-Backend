package capston2024.bustracker.domain.auth;

import org.springframework.stereotype.Component;

@Component
public class AdditionalAuthAPI {
    public boolean authenticate(User user, String studentId, String password) {
        return true;
    }
}
