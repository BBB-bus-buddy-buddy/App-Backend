package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.LicenseVerifyRequestDto;
import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.Organization;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.domain.auth.OAuthAttributes;
import capston2024.bustracker.domain.auth.OrganizationIdGenerator;
import capston2024.bustracker.domain.auth.UserCreator;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final TokenService tokenService;
    private final PasswordEncoderService passwordEncoderService; // PasswordEncoder 대신 사용

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
     * 조직 생성 및 관리자 계정 자동 발급
     */
    @Transactional
    public Map<String, String> createOrganizationAndAdmin(String organizationName, String adminName) {
        // 조직 ID 생성 (OrganizationIdGenerator 사용)
        String organizationId = OrganizationIdGenerator.generateOrganizationId(organizationName);

        // 조직 생성
        Organization organization = organizationService.generateOrganization(organizationId, organizationName);

        log.info("생성된 organization : {}", organization);

        // 비밀번호 자동 생성
        String rawPassword = generateRandomPassword(10); // 10자리 랜덤 비밀번호
        String encodedPassword = passwordEncoderService.encode(rawPassword); // 비밀번호 암호화


        // 조직 관리자 계정 생성 - 이메일은 organizationId@bustracker.org 형식으로
        String email = organizationId + "@bustracker.org";

        // 이메일 중복 확인 (필요한 경우 숫자 추가)
        int suffix = 1;
        while (userRepository.findByEmail(email).isPresent()) {
            email = organizationId + suffix + "@bustracker.org";
            suffix++;
        }

        // 관리자 계정 생성
        User newAdmin = User.builder()
                .email(email)
                .name(adminName)
                .role(Role.STAFF)
                .organizationId(organizationId)
                .password(encodedPassword) // 암호화된 비밀번호 저장
                .myStations(new ArrayList<>())
                .build();

        // 저장
        userRepository.save(newAdmin);

        // 발급된 정보 반환
        Map<String, String> accountInfo = new HashMap<>();
        accountInfo.put("organizationName", organizationName);
        accountInfo.put("organizationId", organizationId);
        accountInfo.put("adminName", adminName);
        accountInfo.put("adminId", organizationId); // 조직 ID = 관리자 ID
        accountInfo.put("email", email);
        accountInfo.put("password", rawPassword);

        return accountInfo;
    }

    /**
     * 랜덤 비밀번호 생성
     */
    private String generateRandomPassword(int length) {
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        String numbers = "0123456789";
        String specialChars = "!@#$%^&*()-_=+";
        String allChars = upperCase + lowerCase + numbers + specialChars;

        Random random = new Random();
        StringBuilder password = new StringBuilder();

        // 각 문자 타입에서 최소 1개씩 포함
        password.append(upperCase.charAt(random.nextInt(upperCase.length())));
        password.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
        password.append(numbers.charAt(random.nextInt(numbers.length())));
        password.append(specialChars.charAt(random.nextInt(specialChars.length())));

        // 나머지 문자 랜덤 생성
        for (int i = 4; i < length; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // 문자열 섞기
        char[] passwordArray = password.toString().toCharArray();
        for (int i = 0; i < passwordArray.length; i++) {
            int j = random.nextInt(passwordArray.length);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }

    /**
     * 조직별 관리자 계정 목록 조회
     */
    public List<User> getOrganizationAdmins(String organizationId) {
        // 특정 조직의 STAFF 권한을 가진 사용자 조회
        return userRepository.findByOrganizationIdAndRole(organizationId, Role.STAFF);
    }

    /**
     * 조직 관리자 비밀번호 리셋
     */
    @Transactional
    public Map<String, String> resetStaffPassword(String organizationId) {
        // 이메일 형식: organizationId@bustracker.org
        String email = organizationId + "@bustracker.org";

        // 사용자 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("해당 조직 ID의 관리자를 찾을 수 없습니다."));

        // STAFF 권한 확인
        if (user.getRole() != Role.STAFF) {
            throw new BusinessException("해당 계정은 조직 관리자 계정이 아닙니다.");
        }

        // 새 비밀번호 생성
        String rawPassword = generateRandomPassword(10);
        String encodedPassword = passwordEncoderService.encode(rawPassword);

        // 비밀번호 업데이트
        user.setPassword(encodedPassword);
        userRepository.save(user);

        // 결과 반환
        Map<String, String> passwordInfo = new HashMap<>();
        passwordInfo.put("organizationId", organizationId);
        passwordInfo.put("password", rawPassword);

        return passwordInfo;
    }

    /**
     * OAuth2 로그인 사용자가 총관리자인지 확인
     */
    public boolean isAdmin(OAuth2User principal) {
        if (principal == null) {
            return false;
        }

        User user = getUserFromPrincipal(principal);
        return user != null && Role.ADMIN.getKey().equals(user.getRoleKey());
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

    /**
     * 운전자 권한으로 업그레이드
     * @param principal OAuth2User
     * @param organizationCode 조직 코드
     * @param requestDto 운전자 추가 정보
     * @return 업그레이드 성공 여부
     */
    @Transactional
    public boolean rankUpGuestToDriver(OAuth2User principal, String organizationCode, LicenseVerifyRequestDto requestDto) {
        User user = getUserFromPrincipal(principal);
        if (user == null) {
            throw new RuntimeException("존재하지 않는 회원입니다.");
        }

        // 이미 DRIVER 권한을 갖고 있는 경우
        if (user.getRole() == Role.DRIVER) {
            log.info("이미 DRIVER 권한이 있는 사용자입니다: {}", user.getEmail());
            return true;
        }

        // 조직 존재 여부 확인
        Organization organization = organizationService.getOrganization(organizationCode);

        // 운전자 추가 정보 저장 (필요한 경우 별도 엔티티 생성)
        // TODO: 운전자 면허 정보 등 추가 데이터 저장 로직 구현

        // DRIVER 권한으로 업그레이드
        user.updateRole(Role.DRIVER);
        user.setOrganizationId(organization.getId());
        userRepository.save(user);

        log.info("사용자 권한이 DRIVER로 업그레이드되었습니다: {}", user.getEmail());
        return true;
    }
}