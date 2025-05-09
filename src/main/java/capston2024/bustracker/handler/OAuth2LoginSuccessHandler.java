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

            // 사용자 역할 확인 (Authentication 객체에서 권한 정보 추출)
            boolean isDriver = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(Role.DRIVER.getKey()));

            // 디바이스 타입 감지
            String userAgent = request.getHeader("User-Agent");
            String redirectUrl = determineRedirectUrl(userAgent, accessToken, isDriver);

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
     * User-Agent 헤더를 분석하여 디바이스 타입과 사용자 역할에 맞는 리다이렉트 URL을 결정합니다.
     *
     * @param userAgent 사용자의 디바이스 정보
     * @param accessToken 발급된 액세스 토큰
     * @param isDriver 운전자 여부
     * @return 적절한 리다이렉트 URL
     */
    private String determineRedirectUrl(String userAgent, String accessToken, boolean isDriver) throws IOException {
        String encodedToken = URLEncoder.encode(accessToken, StandardCharsets.UTF_8.toString());
        String appSchemeUri;

        log.info("User-Agent: {}", userAgent);
        log.info("사용자 역할: {}", isDriver ? "운전자" : "일반 사용자");

        // User-Agent 문자열에서 디바이스 타입 감지
        if (userAgent != null) {
            if (userAgent.contains("Android")) {
                // 운전자와 일반 사용자 구분
                appSchemeUri = isDriver ? DRIVER_ANDROID_APP_SCHEME_URL : ANDROID_APP_SCHEME_URI;
            } else if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iPod")) {
                // 운전자와 일반 사용자 구분
                appSchemeUri = isDriver ? DRIVER_IOS_APP_SCHEME_URL : IOS_APP_SCHEME_URI;
            } else {
                log.info("웹 로그인 감지");
                return UriComponentsBuilder
                        .fromUriString("/admin/dashboard")
                        .queryParam("token", encodedToken)
                        .build(false)
                        .toUriString();
            }
        } else {
            // 그 외는 웹으로 간주
            log.info("웹 로그인 감지");
            return UriComponentsBuilder
                    .fromUriString("/admin/dashboard")
                    .queryParam("token", encodedToken)
                    .build(false)
                    .toUriString();
        }

        log.info("결정된 앱 스킴 URI: {}", appSchemeUri);

        // 리다이렉트 URL 생성
        return UriComponentsBuilder
                .fromUriString(appSchemeUri)
                .queryParam("token", encodedToken)
                .build(false)  // URL 인코딩 비활성화 (이미 인코딩했으므로)
                .toUriString();
    }
}