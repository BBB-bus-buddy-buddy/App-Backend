package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.CodeRequestDTO;
import capston2024.bustracker.config.dto.LicenseVerifyRequestDto;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.DriverService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final DriverService driverService;

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUser(@AuthenticationPrincipal OAuth2User principal) {
        log.info("Received request for user details. Principal: {}", principal);
        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }
        Map<String, Object> obj = authService.getUserDetails(principal);
        log.debug("User details retrieved successfully: {}", obj);
        return ResponseEntity.ok(new ApiResponse<>(obj, "성공적으로 유저의 정보를 조회하였습니다."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Boolean>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(new ApiResponse<>(true, "성공적으로 로그아웃을 하였습니다."));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<ApiResponse<Boolean>> withdrawal(
            @AuthenticationPrincipal OAuth2User principal,
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("회원탈퇴 요청 - Principal: {}", principal);

        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        boolean isSuccess = authService.withdrawUser(principal);

        if (isSuccess) {
            // 회원탈퇴 성공 후 로그아웃 처리
            authService.logout(request, response);
            return ResponseEntity.ok(new ApiResponse<>(true, "회원탈퇴가 성공적으로 처리되었습니다."));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "회원탈퇴 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 일반 사용자 → 인증된 사용자 등급 승급
     */
    @PostMapping("/rankUp")
    public ResponseEntity<ApiResponse<Boolean>> rankUpUser(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody CodeRequestDTO requestDTO) {

        log.info("사용자 권한 승급 요청 - 코드: {}", requestDTO.getCode());

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        boolean isRankUp = authService.rankUpGuestToUser(principal, requestDTO.getCode());

        if (isRankUp) {
            return ResponseEntity.ok(new ApiResponse<>(true, "성공적으로 사용자 등급이 업그레이드되었습니다."));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "잘못된 인증 코드입니다."));
        }
    }

    @PostMapping("/driver-verify-and-rankup")
    public ResponseEntity<ApiResponse<Boolean>> verifyDriverLicenseAndRankUp(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody LicenseVerifyRequestDto requestDto) {

        log.info("운전면허 검증 및 권한 업그레이드 요청");

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        try {
            // 1. 운전면허 검증
            Map<String, String> verificationResult = driverService.verifyLicense(requestDto);

            String authenticity = verificationResult.get("resAuthenticity");
            if (!"1".equals(authenticity) && !"2".equals(authenticity)) {
                log.warn("운전면허 검증 실패: {}", verificationResult.get("resAuthenticityDesc1"));
                return ResponseEntity.badRequest().body(
                        new ApiResponse<>(false, verificationResult.get("resAuthenticityDesc1"))
                );
            }

            // 2. 권한 업그레이드
            // 고정값인 organization 값 사용
            String organizationCode = driverService.getDefaultOrganization();

            boolean isRankUp = authService.rankUpGuestToDriver(principal, organizationCode, requestDto);

            if (isRankUp) {
                return ResponseEntity.ok(
                        new ApiResponse<>(true, "운전면허 검증 및 권한 업그레이드가 완료되었습니다.")
                );
            } else {
                return ResponseEntity.badRequest().body(
                        new ApiResponse<>(false, "권한 업그레이드에 실패했습니다.")
                );
            }

        } catch (BusinessException e) {
            log.error("운전면허 검증 및 권한 업그레이드 중 비즈니스 오류 발생: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, e.getMessage())
            );
        } catch (Exception e) {
            log.error("운전면허 검증 및 권한 업그레이드 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ApiResponse<>(false, "처리 중 오류가 발생했습니다.")
            );
        }
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("인가되지 않은 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(false, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("찾을 수 없는 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(false, ex.getMessage()));
    }
}