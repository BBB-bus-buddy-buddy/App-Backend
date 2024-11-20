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
    private static final String APP_SCHEME_URI = "org.reactjs.native.example.capstonBBBBNative://oauth2callback";
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


            // URL 인코딩된 토큰을 사용하여 리다이렉트 URL 생성
            String encodedToken = URLEncoder.encode(accessToken, StandardCharsets.UTF_8.toString());
            String redirectUrl = UriComponentsBuilder
                    .fromUriString(APP_SCHEME_URI)
                    .queryParam("token", encodedToken)
                    .build(false)  // URL 인코딩 비활성화 (이미 인코딩했으므로)
                    .toUriString();

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

    // determineTargetUrl 메서드는 이제 필요 없으므로 제거
}