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

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        TokenInfo existingToken = tokenService.findByUserName(username);

        String accessToken;
        try {
            // 토큰 처리 로직
            if (existingToken != null && tokenProvider.validateToken(existingToken.getRefreshToken())) {
                long refreshTokenRemainTime = tokenProvider.getTokenExpirationTime(existingToken.getRefreshToken());

                if (refreshTokenRemainTime > REFRESH_TOKEN_ROTATION_TIME) {
                    accessToken = tokenProvider.reissueAccessToken(existingToken.getAccessToken());
                } else {
                    accessToken = tokenProvider.generateAccessToken(authentication);
                    tokenProvider.generateRefreshToken(authentication, accessToken);
                }
            } else {
                accessToken = tokenProvider.generateAccessToken(authentication);
                tokenProvider.generateRefreshToken(authentication, accessToken);
            }

            // 디바이스 타입 감지 및 역할 기반 리다이렉션
            String userAgent = request.getHeader("User-Agent");
            String redirectUrl = determineRedirectUrl(userAgent, accessToken, authentication);

            // CORS 헤더 추가
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
     */
    private String determineRedirectUrl(String userAgent, String accessToken, Authentication authentication) throws IOException {
        // 토큰 URL 인코딩
        String encodedToken = URLEncoder.encode(accessToken, StandardCharsets.UTF_8.toString());
        String appSchemeUri;

        // 사용자 역할 확인
        boolean isGuest = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.GUEST.getKey()));
        boolean isUser = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.USER.getKey()));
        boolean isDriver = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.DRIVER.getKey()));
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.STAFF.getKey()));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));

        // 사용자 역할 로깅
        String roleStr = isAdmin ? "총관리자" :
                isStaff ? "조직 관리자" :
                        isDriver ? "운전자" :
                                isUser ? "인증된 사용자" :
                                        isGuest ? "게스트" : "알 수 없음";
        log.info("User-Agent: {}", userAgent);
        log.info("사용자 역할: {}", roleStr);

        // 관리자/조직 관리자는 웹 대시보드로 리다이렉트
        if (isAdmin || isStaff) {
            log.info("관리자/조직 관리자 - 웹 대시보드로 리다이렉트");
            return UriComponentsBuilder
                    .fromUriString("/admin/dashboard")
                    .queryParam("token", encodedToken)
                    .build(false)
                    .toUriString();
        }

        // 앱 사용자 처리 (GUEST, USER, DRIVER)
        if (userAgent != null) {
            if (userAgent.contains("Android")) {
                // Android 디바이스용 앱 스킴 선택
                appSchemeUri = isDriver ? DRIVER_ANDROID_APP_SCHEME_URL : ANDROID_APP_SCHEME_URI;
            } else if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iPod")) {
                // iOS 디바이스용 앱 스킴 선택
                appSchemeUri = isDriver ? DRIVER_IOS_APP_SCHEME_URL : IOS_APP_SCHEME_URI;
            } else {
                // 웹 브라우저이지만 앱 사용자인 경우 - 앱 다운로드 안내 페이지로 리다이렉트
                log.info("앱 사용자의 웹 브라우저 로그인 감지 - 앱 다운로드 페이지로 리다이렉트");
                return UriComponentsBuilder
                        .fromUriString("/app-download")
                        .queryParam("role", isDriver ? "driver" : "user")
                        .queryParam("token", encodedToken)
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