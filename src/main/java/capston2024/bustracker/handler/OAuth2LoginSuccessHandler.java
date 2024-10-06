package capston2024.bustracker.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.io.IOException;
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(authentication);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(Authentication authentication) {
        if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_GUEST"))) {
            return "http://localhost:3000/enter-code";
        } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")) ||
                authentication.getAuthorities().contains(new SimpleGrantedAuthority("ADMIN"))) {
            return "http://localhost:3000/home";
        } else {
            // 기본 URL (예상치 못한 역할의 경우)
            return "http://localhost:3000/";
        }
    }
}