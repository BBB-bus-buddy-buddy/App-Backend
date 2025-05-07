package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.handler.JwtTokenProvider;
import capston2024.bustracker.repository.UserRepository;
import capston2024.bustracker.service.PasswordEncoderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@Slf4j
public class StaffController {

    private final UserRepository userRepository;
    private final PasswordEncoderService passwordEncoderService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(@RequestBody Map<String, String> loginRequest) {
        String organizationId = loginRequest.get("organizationId");
        String password = loginRequest.get("password");

        if (organizationId == null || password == null) {
            throw new BusinessException("조직 ID와 비밀번호를 모두 입력해주세요.");
        }

        // 이메일 형식: organizationId@bustracker.org
        String email = organizationId + "@bustracker.org";

        // 사용자 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("조직 ID 또는 비밀번호가 일치하지 않습니다."));

        // STAFF 권한 확인
        if (user.getRole() != Role.STAFF) {
            throw new UnauthorizedException("관리자 계정이 아닙니다.");
        }

        // 비밀번호 확인
        if (!passwordEncoderService.matches(password, user.getPassword())) {
            throw new UnauthorizedException("조직 ID 또는 비밀번호가 일치하지 않습니다.");
        }

        // 인증 객체 생성 (OAuth2User 형식으로)
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", user.getId());
        attributes.put("name", user.getName());
        attributes.put("email", user.getEmail());
        attributes.put("organizationId", user.getOrganizationId());

        GrantedAuthority authority = new SimpleGrantedAuthority(user.getRoleKey());
        DefaultOAuth2User oAuth2User = new DefaultOAuth2User(
                Collections.singleton(authority),
                attributes,
                "sub"
        );

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                oAuth2User, null, Collections.singleton(authority));

        // 인증 정보 설정
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 토큰 생성
        String accessToken = tokenProvider.generateAccessToken(authentication);
        tokenProvider.generateRefreshToken(authentication, accessToken);

        // 응답 생성
        Map<String, String> response = new HashMap<>();
        response.put("token", accessToken);
        response.put("name", user.getName());
        response.put("organizationId", user.getOrganizationId());

        return ResponseEntity.ok(new ApiResponse<>(response, "로그인 성공"));
    }
}