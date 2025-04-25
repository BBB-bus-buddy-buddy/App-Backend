package capston2024.bustracker.handler;

import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.auth.TokenInfo;
import capston2024.bustracker.service.TokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;

    // 앱 스킴 URL

    // 여기에 버스기사 앱 스킴 URL 포함할 것 @엄지석
    private static final String IOS_APP_SCHEME_URI = "org.reactjs.native.example.Busbuddybuddy://oauth2callback";
    private static final String ANDROID_APP_SCHEME_URI = "com.busbuddybuddy://oauth2callback";
    private static final long REFRESH_TOKEN_ROTATION_TIME = 1000 * 60 * 60 * 24 * 7L;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        TokenInfo existingToken = tokenService.findByUserName(username);

        String accessToken;
        try {
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

            // 디바이스 타입 감지
            String userAgent = request.getHeader("User-Agent");
            String redirectUrl = determineRedirectUrl(userAgent, accessToken);

            // CORS 헤더 추가
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");

            // 리다이렉트 수행
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            // 에러 로깅
            e.printStackTrace();
            throw new ServletException("Authentication failed");
        }
    }

    /**
     * User-Agent 헤더를 분석하여 디바이스 타입에 맞는 리다이렉트 URL을 결정합니다.
     */
    private String determineRedirectUrl(String userAgent, String accessToken) throws IOException {
        String encodedToken = URLEncoder.encode(accessToken, StandardCharsets.UTF_8.toString());
        String appSchemeUri;

        // User-Agent 문자열에서 디바이스 타입 감지
        if (userAgent != null) {
            if (userAgent.contains("Android")) {
                appSchemeUri = ANDROID_APP_SCHEME_URI;
            } else if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iPod")) {
                appSchemeUri = IOS_APP_SCHEME_URI;
            } else {
                // 기본값으로 iOS 스킴 사용 (또는 필요에 따라 변경)
                appSchemeUri = IOS_APP_SCHEME_URI;
            }
        } else {
            // User-Agent가 null인 경우 기본값 사용
            appSchemeUri = IOS_APP_SCHEME_URI;
        }

        // 리다이렉트 URL 생성
        return UriComponentsBuilder
                .fromUriString(appSchemeUri)
                .queryParam("token", encodedToken)
                .build(false)  // URL 인코딩 비활성화 (이미 인코딩했으므로)
                .toUriString();
    }
}