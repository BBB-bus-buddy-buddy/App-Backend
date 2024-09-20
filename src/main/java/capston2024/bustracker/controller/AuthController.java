package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.SchoolAuthRequestDTO;
import capston2024.bustracker.exception.AdditionalAuthenticationFailedException;
import capston2024.bustracker.service.AuthenticationService;
import io.jsonwebtoken.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 웹 MVC의 컨트롤러 역할
 * 계정 정보 유효성 검사 및 인증 관련 API 제공
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationService authenticationService;

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException, java.io.IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUser(@AuthenticationPrincipal OAuth2User principal) {
        return ResponseEntity.ok(authenticationService.getUserDetails(principal));
    }

    @PostMapping("/school/mail")
    public ResponseEntity<?> authenticateSchoolSendMail(@RequestBody SchoolAuthRequestDTO request, @AuthenticationPrincipal OAuth2User principal){
        boolean isSendMail = authenticationService.sendToSchoolEmail(principal, request.getSchoolEmail(), request.getSchoolName());
        return ResponseEntity.ok(Map.of("sendMail", isSendMail));
    }

    @PostMapping("/school/code")
    public ResponseEntity<?> authenticateSchool(@RequestBody SchoolAuthRequestDTO request, @AuthenticationPrincipal OAuth2User principal) throws AdditionalAuthenticationFailedException, java.io.IOException {
        boolean isAuthenticated = authenticationService.performSchoolAuthentication(principal, request.getSchoolEmail(), request.getSchoolName(), request.getCode());
        return ResponseEntity.ok(Map.of("authenticated", isAuthenticated));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(request, response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}