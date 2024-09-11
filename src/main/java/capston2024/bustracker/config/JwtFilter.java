package capston2024.bustracker.config;

import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.service.JwtService;
import io.jsonwebtoken.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException, java.io.IOException {
        // 허용된 경로에 대한 요청인지 확인하고, 맞다면 필터 체인을 계속 진행합니다.
        if (isPermitAllPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 요청으로부터 토큰을 검증하고 인증 컨텍스트를 설정합니다.
            processTokenAuthentication(request);
            filterChain.doFilter(request, response);
        } catch (BusinessException e) {
            log.error("Token validation error: {}", e.getMessage(), e);
            throw e; // 비즈니스 예외 처리
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INVALID_TOKEN); // 예상치 못한 예외 처리
        }
    }

    // 요청에서 토큰을 추출하고 인증 과정을 처리합니다.
    private void processTokenAuthentication(HttpServletRequest request) {
        HttpSession session = request.getSession();
        String accessToken = getTokenFromSession(session, "accessToken");
        String refreshToken = getTokenFromSession(session, "refreshToken");

        // 액세스 토큰이 유효하면 인증 컨텍스트를 설정합니다.
        if (accessToken != null && jwtService.validateAccessToken(accessToken)) {
            setAuthenticationContext(jwtService.getUserIdByParseToken(accessToken));
        } else if (refreshToken != null) {
            // 리프레시 토큰이 있다면 액세스 토큰을 갱신하고 인증 컨텍스트를 설정합니다.
            String newAccessToken = jwtService.renewAccessTokenUsingRefreshToken(refreshToken);
            session.setAttribute("accessToken", newAccessToken);
            setAuthenticationContext(jwtService.getUserIdByParseToken(newAccessToken));
        }
    }

    // 세션에서 토큰을 가져옵니다.
    private String getTokenFromSession(HttpSession session, String tokenName) {
        return  (String) session.getAttribute(tokenName);
    }

    // 인증 정보를 SecurityContext에 설정합니다.
    private void setAuthenticationContext(Long userId) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, null, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // 요청 URI가 허용된 경로에 속하는지 확인합니다.
    private boolean isPermitAllPath(String requestURI) {
        return requestURI.startsWith("/api/auth/google")
                || requestURI.startsWith("/swagger-ui")
                || requestURI.startsWith("/v3/api-docs") || requestURI.startsWith("/h2-console");
    }
}
