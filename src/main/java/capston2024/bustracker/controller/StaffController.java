package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.domain.auth.TokenInfo;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.TokenException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.handler.JwtTokenProvider;
import capston2024.bustracker.repository.UserRepository;
import capston2024.bustracker.service.PasswordEncoderService;
import capston2024.bustracker.service.TokenService;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@Slf4j
public class StaffController {

    private static final long REFRESH_TOKEN_ROTATION_TIME = 1000 * 60 * 60 * 24 * 7L; // 7일

    private final UserRepository userRepository;
    private final PasswordEncoderService passwordEncoderService;
    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;

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

        // 기존 토큰 확인 로직 추가
        String username = authentication.getName();
        TokenInfo existingToken = tokenService.findByUserName(username);

        String accessToken;
        try {
            // 기존 토큰이 있고 유효한 경우 처리
            if (existingToken != null && tokenProvider.validateToken(existingToken.getRefreshToken())) {
                long refreshTokenRemainTime = tokenProvider.getTokenExpirationTime(existingToken.getRefreshToken());

                // 리프레시 토큰 순환 시간이 남아있으면 액세스 토큰만 재발급
                if (refreshTokenRemainTime > REFRESH_TOKEN_ROTATION_TIME) {
                    accessToken = tokenProvider.reissueAccessToken(existingToken.getAccessToken());
                } else {
                    // 리프레시 토큰 순환 시간이 임박하면 모든 토큰 재발급
                    accessToken = tokenProvider.generateAccessToken(authentication);
                    tokenProvider.generateRefreshToken(authentication, accessToken);
                }
            } else {
                // 기존 토큰이 없거나 유효하지 않은 경우 신규 발급
                accessToken = tokenProvider.generateAccessToken(authentication);
                tokenProvider.generateRefreshToken(authentication, accessToken);
            }
        } catch (ExpiredJwtException e) {
            log.warn("만료된 토큰이 감지되어 새로 발급합니다: {}", username);
            accessToken = tokenProvider.generateAccessToken(authentication);
            tokenProvider.generateRefreshToken(authentication, accessToken);
        } catch (TokenException e) {
            log.error("토큰 처리 중 오류 발생: {}", e.getMessage());
            throw new UnauthorizedException("토큰 처리 중 오류가 발생했습니다.");
        } catch (Exception e) {
            log.error("예기치 않은 오류 발생", e);
            // 오류 발생 시 새로 발급
            accessToken = tokenProvider.generateAccessToken(authentication);
            tokenProvider.generateRefreshToken(authentication, accessToken);
        }

        // 응답 생성
        Map<String, String> response = new HashMap<>();
        response.put("token", accessToken);
        response.put("name", user.getName());
        response.put("organizationId", user.getOrganizationId());

        return ResponseEntity.ok(new ApiResponse<>(response, "로그인 성공"));
    }

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());

        HttpStatus status = switch (ex.getErrorCode()) {
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_ENTITY -> HttpStatus.CONFLICT;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_TOKEN, TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("인증 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 토큰 예외 처리
     */
    @ExceptionHandler({TokenException.class, ExpiredJwtException.class})
    public ResponseEntity<ApiResponse<Void>> handleTokenException(Exception ex) {
        log.error("토큰 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, "토큰이 유효하지 않거나 만료되었습니다."));
    }

    /**
     * 잘못된 요청 형식 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("잘못된 요청 형식: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, "잘못된 요청 형식입니다."));
    }

    /**
     * 일반 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("예기치 않은 오류 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
    }
}