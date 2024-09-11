package capston2024.bustracker.controller;

import capston2024.bustracker.config.auth.dto.GoogleInfoDto;
import capston2024.bustracker.service.JwtService;
import io.jsonwebtoken.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * ** 웹 MVC의 컨트롤러 역할 **
 * 계정 정보 유효성 검사
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    JwtService jwtService;

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException, java.io.IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    @GetMapping("/oauth2/code/google")
    public ResponseEntity<String> googleCallback(@AuthenticationPrincipal OAuth2User oAuth2User) {
        GoogleInfoDto userInfo = new GoogleInfoDto(Objects.requireNonNull(oAuth2User.getAttribute("user")));
        String token = jwtService.generateToken(userInfo);
        return ResponseEntity.ok(token);
    }

    @GetMapping("/user")
    public ResponseEntity<GoogleInfoDto> getUser(@RequestHeader("Authorization") String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            if (jwtService.validateToken(token)) {
                GoogleInfoDto userInfo = jwtService.getUserInfoFromToken(token);
                return ResponseEntity.ok(userInfo);
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    @GetMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT는 stateless이므로 서버에서 특별히 할 일은 없습니다.
        // 클라이언트에서 토큰을 삭제하도록 안내합니다.
        return ResponseEntity.ok().build();
    }
}