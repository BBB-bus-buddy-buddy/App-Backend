package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.domain.Organization;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.handler.JwtTokenProvider;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "총관리자 관리", description = "시스템 총관리자 전용 기능을 제공하는 API - 조직 생성, 관리자 계정 관리 등")
public class AdminController {

    private final AuthService authService;
    private final OrganizationService organizationService;
    private final JwtTokenProvider tokenProvider;

    @GetMapping("/login")
    @Operation(
            summary = "관리자 로그인 페이지",
            description = "총관리자용 로그인 페이지를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 페이지 반환 성공")
    })
    public String adminLoginPage() {
        return "admin/login";
    }

    @GetMapping("/dashboard")
    @Operation(
            summary = "총관리자 대시보드",
            description = "총관리자용 대시보드 페이지를 반환합니다. 모든 조직 정보가 포함되며, 인증된 총관리자만 접근할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "대시보드 페이지 반환 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "로그인 페이지로 리디렉션"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "총관리자 권한 없음")
    })
    public String adminDashboard(
            @Parameter(description = "JWT 인증 토큰 (선택사항)", required = false) @RequestParam(required = false) String token,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(hidden = true) Model model) {

        log.info("Dashboard 접근 - Principal: {}, Token: {}", principal, token);

        OAuth2User authenticatedAdmin = authenticateAdmin(principal, token);
        if (authenticatedAdmin == null) {
            return "redirect:/admin/login";
        }

        List<Organization> organizations = organizationService.getAllOrganizations();
        model.addAttribute("organizations", organizations);

        if (token != null) {
            model.addAttribute("token", token);
        }

        return "admin/dashboard";
    }

    @PostMapping("/api/organizations")
    @ResponseBody
    @Operation(
            summary = "새로운 조직 및 관리자 계정 생성",
            description = "새로운 조직을 생성하고 해당 조직의 관리자 계정을 자동으로 생성합니다. 생성된 계정 정보가 반환됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조직 및 관리자 계정 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 필드 누락 또는 잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "총관리자 권한 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 조직명")
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> createOrganizationAndAdmin(
            @Parameter(description = "JWT 인증 토큰 (선택사항)", required = false) @RequestParam(required = false) String token,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "조직 생성 요청 데이터", required = true) @RequestBody Map<String, String> request) {

        OAuth2User authenticatedAdmin = authenticateAdmin(principal, token);
        if (authenticatedAdmin == null) {
            throw new UnauthorizedException("총관리자 권한이 필요합니다.");
        }

        String organizationName = request.get("organizationName");
        String adminName = request.get("adminName");

        if (organizationName == null || adminName == null) {
            throw new BusinessException("모든 필드를 입력해주세요.");
        }

        Map<String, String> accountInfo = authService.createOrganizationAndAdmin(organizationName, adminName);

        return ResponseEntity.ok(new ApiResponse<>(accountInfo, "조직 및 관리자 계정이 생성되었습니다."));
    }

    @GetMapping("/api/organization-admins")
    @ResponseBody
    @Operation(
            summary = "조직별 관리자 계정 목록 조회",
            description = "특정 조직에 속한 관리자(STAFF) 계정들의 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 계정 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "총관리자 권한 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 조직")
    })
    public ResponseEntity<ApiResponse<List<User>>> getOrganizationAdmins(
            @Parameter(description = "JWT 인증 토큰 (선택사항)", required = false) @RequestParam(required = false) String token,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "조회할 조직의 ID", required = true) @RequestParam String organizationId) {

        OAuth2User authenticatedAdmin = authenticateAdmin(principal, token);
        if (authenticatedAdmin == null) {
            throw new UnauthorizedException("총관리자 권한이 필요합니다.");
        }

        List<User> admins = authService.getOrganizationAdmins(organizationId);
        return ResponseEntity.ok(new ApiResponse<>(admins, "조직 관리자 계정 목록 조회 완료"));
    }

    @PostMapping("/api/reset-password")
    @ResponseBody
    @Operation(
            summary = "관리자 비밀번호 리셋",
            description = "조직 관리자의 비밀번호를 리셋하고 새로운 임시 비밀번호를 생성합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "비밀번호 리셋 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "조직 ID 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "총관리자 권한 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "해당 조직의 관리자를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @Parameter(description = "JWT 인증 토큰 (선택사항)", required = false) @RequestParam(required = false) String token,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "비밀번호 리셋 요청 데이터", required = true) @RequestBody Map<String, String> request) {

        OAuth2User authenticatedAdmin = authenticateAdmin(principal, token);
        if (authenticatedAdmin == null) {
            throw new UnauthorizedException("총관리자 권한이 필요합니다.");
        }

        String organizationId = request.get("organizationId");

        if (organizationId == null) {
            throw new BusinessException("조직 ID를 입력해주세요.");
        }

        Map<String, String> newPasswordInfo = authService.resetStaffPassword(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(newPasswordInfo, "비밀번호가 성공적으로 리셋되었습니다."));
    }

    // 기존 private 메서드들과 예외 핸들러들 유지
    private OAuth2User authenticateAdmin(OAuth2User principal, String token) {
        if (principal != null) {
            log.info("OAuth2 인증된 사용자: {}", principal.getName());
            if (authService.isAdmin(principal)) {
                return principal;
            }
            log.warn("관리자 권한이 없는 사용자: {}", principal.getName());
            return null;
        } else if (token != null && !token.isEmpty()) {
            log.info("토큰 파라미터로 인증 시도");
            try {
                Authentication authentication = tokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                OAuth2User tokenPrincipal = (OAuth2User) authentication.getPrincipal();
                if (authService.isAdmin(tokenPrincipal)) {
                    log.info("토큰 인증 성공: {}", tokenPrincipal.getName());
                    return tokenPrincipal;
                }
                log.warn("관리자 권한이 없는 토큰: {}", tokenPrincipal.getName());
                return null;
            } catch (Exception e) {
                log.error("토큰 인증 실패: {}", e.getMessage());
                return null;
            }
        } else {
            log.warn("인증 정보 없음");
            return null;
        }
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("인증 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}