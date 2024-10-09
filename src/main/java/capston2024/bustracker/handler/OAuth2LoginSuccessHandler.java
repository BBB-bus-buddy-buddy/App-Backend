package capston2024.bustracker.handler;

import capston2024.bustracker.config.status.Role;
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

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private static final String URI = "http://localhost:3000";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        // accessToken, refreshToken 발급
        String accessToken = tokenProvider.generateAccessToken(authentication);
        tokenProvider.generateRefreshToken(authentication, accessToken);

        String redirectUri = determineTargetUrl(authentication);

        // 토큰 전달을 위한 redirect
        String redirectUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", accessToken)
                .build().toUriString();

        response.sendRedirect(redirectUrl);
    }

        protected String determineTargetUrl(Authentication authentication) {
            if (authentication.getAuthorities().contains(new SimpleGrantedAuthority(Role.GUEST.getKey()))) {
                return URI + "/enter-code";
            } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority(Role.USER.getKey())) ||
                    authentication.getAuthorities().contains(new SimpleGrantedAuthority(Role.ADMIN.getKey()))) {
                return URI + "/home";
            } else {
                return URI;
            }
        }

}