package capston2024.bustracker.service;

import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.Organization;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.domain.auth.OAuthAttributes;
import capston2024.bustracker.domain.auth.UserCreator;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final TokenService tokenService;

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
    public boolean rankUpGuestToUser(OAuth2User principal, String organizationId) {
        User user = getUserFromPrincipal(principal);
        if (user == null) {
            throw new RuntimeException("존재하지 않는 회원입니다.");
        }

        // 이미 USER 권한을 갖고 있는 경우
        if (user.getRole() == Role.USER || user.getRole() == Role.ADMIN) {
            log.info("이미 인증된 사용자입니다: {}", user.getEmail());
            return true;
        }

        Organization organization = organizationService.getOrganization(organizationId);
        user.updateRole(Role.USER);
        user.setOrganizationId(organization.getId());
        userRepository.save(user);
        return true;
    }

    /**
     * 회원 탈퇴 기능
     * 1. User 권한을 GUEST로 변경
     * 2. OrganizationId를 빈 문자열로 변경
     * 3. 해당 사용자의 토큰 삭제
     */
    @Transactional
    public boolean withdrawUser(OAuth2User principal) {
        try {
            User user = getUserFromPrincipal(principal);
            if (user == null) {
                throw new RuntimeException("존재하지 않는 회원입니다.");
            }

            log.info("회원탈퇴 처리 시작 - 이메일: {}, 현재 권한: {}", user.getEmail(), user.getRole());

            // 1. 권한을 GUEST로 변경
            user.updateRole(Role.GUEST);

            // 2. 조직 ID를 빈 문자열로 변경
            user.setOrganizationId("");

            // 3. 저장
            userRepository.save(user);

            // 4. 사용자 토큰 삭제
            tokenService.deleteByUsername(user.getEmail());

            log.info("회원탈퇴 처리 완료 - 이메일: {}", user.getEmail());
            return true;
        } catch (Exception e) {
            log.error("회원탈퇴 처리 중 오류 발생: {}", e.getMessage(), e);
            return false;
        }
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
        return userRepository.findByEmail(email).orElseThrow(()->new UnauthorizedException("사용자가 인증되지 않았습니다"));
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
        return Map.of(
                "인증 상태", true,
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRoleKey(),
                "organizationId", user.getOrganizationId()
        );
    }
}