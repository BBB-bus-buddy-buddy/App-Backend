package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.SchoolAuthRequestDTO;
import capston2024.bustracker.exception.AdditionalAuthenticationFailedException;
import capston2024.bustracker.service.AuthService;
import io.jsonwebtoken.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private AuthService authService;

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getUser(@AuthenticationPrincipal OAuth2User principal) {
        Map<String, Object> obj = authService.getUserDetails(principal);
        return ResponseEntity.ok(new ApiResponse<>(obj, "성공적으로 유저의 정보를 조회하였습니다."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Boolean>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(new ApiResponse<>(true, "성공적으로 로그아웃을 하였습니다."));
    }
}