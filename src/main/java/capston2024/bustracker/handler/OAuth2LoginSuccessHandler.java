package capston2024.bustracker.handler;

import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.auth.TokenInfo;
import capston2024.bustracker.service.TokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 로그인 성공 후 처리를 담당하는 핸들러
 * - 앱 사용자(GUEST, USER, DRIVER): 모바일 앱으로 리다이렉트
 * - 웹 사용자(STAFF, ADMIN): 웹 대시보드로 리다이렉트
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;

    // 앱 스킴 URL 정의
    private static final String DRIVER_IOS_APP_SCHEME_URL = "com.driver://auth/oauth2callback";
    private static final String DRIVER_ANDROID_APP_SCHEME_URL = "com.driver://auth/oauth2callback";
    private static final String IOS_APP_SCHEME_URI = "org.reactjs.native.example.Busbuddybuddy://oauth2callback";
    private static final String ANDROID_APP_SCHEME_URI = "com.busbuddybuddy://oauth2callback";
    private static final long REFRESH_TOKEN_ROTATION_TIME = 1000 * 60 * 60 * 24 * 7L; // 7일

    /**
     * 인증 성공 시 호출되는 메서드
     * 사용자 토큰을 관리하고 적절한 애플리케이션으로 리다이렉트
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
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

            // 디바이스 타입 감지 및 역할 기반 리다이렉션 처리
            String userAgent = request.getHeader("User-Agent");
            String redirectUrl = determineRedirectUrl(userAgent, accessToken, authentication);

            // CORS 헤더 설정
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");

            // 리다이렉트 수행
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("인증 성공 처리 중 오류 발생", e);
            throw new ServletException("Authentication failed");
        }
    }

    /**
     * 사용자 디바이스와 역할에 따른 리다이렉트 URL 결정
     * - 앱 사용자(GUEST, USER, DRIVER): 모바일 앱으로 리다이렉트
     * - 웹 사용자(STAFF, ADMIN): 웹 대시보드로 리다이렉트
     */
    private String determineRedirectUrl(String userAgent, String accessToken, Authentication authentication) throws IOException {
        // 토큰 URL 인코딩
        String encodedToken = URLEncoder.encode(accessToken, StandardCharsets.UTF_8.toString());

        // 사용자 역할 확인
        boolean isDriver = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.DRIVER.getKey()));
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.STAFF.getKey()));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));

        // 웹 사용자(STAFF, ADMIN)는 항상 웹 대시보드로 리다이렉트
        if (isStaff || isAdmin) {
            log.info("웹 사용자(관리자/조직 관리자) - 웹 대시보드로 리다이렉트");
            return UriComponentsBuilder
                    .fromUriString("/admin/dashboard")
                    .queryParam("token", encodedToken)
                    .build(false)
                    .toUriString();
        }

        // 앱 사용자(GUEST, USER, DRIVER) 처리
        // 디바이스에 따른 적절한 앱 스킴 선택
        String appSchemeUri;

        if (userAgent != null) {
            if (userAgent.contains("Android")) {
                // Android 디바이스용 앱 스킴 선택
                appSchemeUri = isDriver ? DRIVER_ANDROID_APP_SCHEME_URL : ANDROID_APP_SCHEME_URI;
                log.info("Android 디바이스 감지 - {} 앱으로 리다이렉트", isDriver ? "운전자" : "사용자");
            } else if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iPod")) {
                // iOS 디바이스용 앱 스킴 선택
                appSchemeUri = isDriver ? DRIVER_IOS_APP_SCHEME_URL : IOS_APP_SCHEME_URI;
                log.info("iOS 디바이스 감지 - {} 앱으로 리다이렉트", isDriver ? "운전자" : "사용자");
            } else {
                // 웹 브라우저이지만 앱 사용자(GUEST, USER, DRIVER)인 경우
                // 웹에서 로그인했지만 모바일 앱 사용자를 위한 안내 페이지로 리다이렉트
                log.info("앱 사용자의 웹 브라우저 로그인 감지 - 앱 다운로드 안내 페이지로 리다이렉트");
                return UriComponentsBuilder
                        .fromUriString("/app-download")
                        .queryParam("role", isDriver ? "driver" : "user")
                        .queryParam("token", encodedToken) // 토큰 포함 (선택적)
                        .build(false)
                        .toUriString();
            }
        } else {
            // User-Agent 없는 경우 기본 웹 페이지로 리다이렉트
            log.info("User-Agent 없음 - 기본 페이지로 리다이렉트");
            return UriComponentsBuilder
                    .fromUriString("/")
                    .queryParam("token", encodedToken)
                    .build(false)
                    .toUriString();
        }

        log.info("결정된 앱 스킴 URI: {}", appSchemeUri);

        // 앱 스킴 URL에 토큰 추가하여 리다이렉트
        return UriComponentsBuilder
                .fromUriString(appSchemeUri)
                .queryParam("token", encodedToken)
                .build(false)  // URL 인코딩 비활성화 (이미 인코딩했으므로)
                .toUriString();
    }
}