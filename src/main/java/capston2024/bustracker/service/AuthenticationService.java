package capston2024.bustracker.service;

import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.auth.AdditionalAuthAPI;
import capston2024.bustracker.domain.auth.OAuthAttributes;
import capston2024.bustracker.domain.auth.User;
import capston2024.bustracker.domain.auth.UserCreator;
import capston2024.bustracker.exception.AdditionalAuthenticationFailedException;
import capston2024.bustracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final AdditionalAuthAPI additionalAuthApi;

    @Transactional
    public User authenticateUser(OAuthAttributes attributes) {
        return userRepository.findByEmail(attributes.getEmail())
                .orElseGet(() -> createNewUser(attributes));
    }

    private User createNewUser(OAuthAttributes attributes) {
        User newUser = UserCreator.createUserFrom(attributes);
        return userRepository.save(newUser);
    }

    @Transactional
    public boolean performSchoolAuthentication(OAuth2User principal, String studentId, String password) throws AdditionalAuthenticationFailedException {
        User user = getUserFromPrincipal(principal);
        if (user == null) {
            throw new RuntimeException("존재하지 않는 회원입니다.");
        }

        boolean isAuthenticated = additionalAuthApi.authenticate(user, studentId, password);
        if (isAuthenticated) {
            user.updateRole(Role.USER);
            userRepository.save(user);
            return true;
        } else {
            throw new AdditionalAuthenticationFailedException(STR."기관 인증 실패: \{user.getEmail()}");
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
    }

    private User getUserFromPrincipal(OAuth2User principal) {
        String email = (String) principal.getAttributes().get("email");
        return userRepository.findByEmail(email).orElse(null);
    }

    public Map<String, Object> getUserDetails(OAuth2User principal) {
        User user = getUserFromPrincipal(principal);
        if (user == null) {
            return Map.of("인증 상태", false);
        }
        return Map.of(
                "인증 상태", true,
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRoleKey()
        );
    }
}