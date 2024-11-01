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

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;
    private static final String URI = "http://DevSe.gonetis.com:12599";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        TokenInfo existingToken = tokenService.findByUserName(username);

        String accessToken;
        if (existingToken != null && tokenProvider.validateToken(existingToken.getRefreshToken())) {
            // 사용가능한 토큰이 있으면 계속 사용
            accessToken = tokenProvider.reissueAccessToken(existingToken.getAccessToken());
        } else {
            // 사용 가능한 토큰이 없으면 새로 토큰을 생성함
            accessToken = tokenProvider.generateAccessToken(authentication);
            tokenProvider.generateRefreshToken(authentication, accessToken);
        }

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