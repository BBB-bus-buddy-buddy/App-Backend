package capston2024.bustracker.service;

import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.School;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.domain.auth.*;
import capston2024.bustracker.exception.AdditionalAuthenticationFailedException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.repository.SchoolRepository;
import capston2024.bustracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final SchoolService schoolService;

    // 이메일 검증 -> 이메일을 찾을 수 없을 시 새로운 유저 생성 로직으로 넘어감
    @Transactional
    public User authenticateUser(OAuthAttributes attributes) {
        return userRepository.findByEmail(attributes.getEmail())
                .orElseGet(() -> createNewUser(attributes));
    }

    // 새로운 유저 생성
    private User createNewUser(OAuthAttributes attributes) {
        User newUser = UserCreator.createUserFrom(attributes);
        return userRepository.save(newUser);
    }


    // 학교 인증후 인증 완료 시 유저의 역할이 USER 로 변경됨
    public boolean rankUpGuestToUser(OAuth2User principal, String schoolName) {
        User user = getUserFromPrincipal(principal);
        if (user == null) {
            throw new RuntimeException("존재하지 않는 회원입니다.");
        }
        School school = schoolService.getSchool(schoolName);
        user.updateRole(Role.USER);
        user.setOrganizationId(school.getId());
        userRepository.save(user);
        return true;
    }


    //로그아웃
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
    }

    /**
     * 유저 정보 반환
     * @param principal
     * @return User Class
     */
    private User getUserFromPrincipal(OAuth2User principal) {
        String email = (String) principal.getAttributes().get("email");
        return userRepository.findByEmail(email).orElse(null);
    }

    /**
     * 관리자 검증 로직
     * @param principal
     */
    void validateAdmin(OAuth2User principal) {
        if (!isAdmin(principal)) {
            throw new UnauthorizedException("해당 유저에게 권한이 없습니다.");
        }
    }

    /**
     * 관리자 검증 로직
     * @param principal
     * @return
     */
    private boolean isAdmin(OAuth2User principal) {
        Map<String,Object> obj = getUserDetails(principal);
        return obj.get("role").equals("ADMIN");
    }


    /**
     * 유저 정보를 Map<키, 값> 형태로 반환
     * @param principal OAuth2User
     * @return Map(String, Object)
     * 인증상태, bool
     * name, string
     * email, string
     * role string
     */
    public Map<String, Object> getUserDetails(OAuth2User principal) {
        User user = getUserFromPrincipal(principal);
        if (user == null) {
            return Map.of("인증 상태", false);
        }
        School school = schoolService.getSchoolByOrganizationId(user.getOrganizationId());
        return Map.of(
                "인증 상태", true,
                "name", user.getName(),
                "email", user.getEmail(),
                "picture", user.getPicture(),
                "role", user.getRoleKey(),
                "school", school.getName()
        );
    }
}