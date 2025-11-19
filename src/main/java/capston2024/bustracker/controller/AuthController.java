package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.AppleLoginRequestDTO;
import capston2024.bustracker.config.dto.CodeRequestDTO;
import capston2024.bustracker.config.dto.DriverUpgradeRequestDTO;
import capston2024.bustracker.domain.Driver;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.domain.auth.DriverCreator;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.handler.JwtTokenProvider;
import capston2024.bustracker.service.AppleAuthService;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.DriverService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

// Swagger 어노테이션 추가
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.HashMap;
import java.util.Map;

/**
 * 웹 MVC의 컨트롤러 역할
 * 계정 정보 유효성 검사 및 인증 관련 API 제공
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "인증 및 사용자 관리 관련 API")
public class AuthController {

    private final AuthService authService;
    private final DriverService driverService;
    private final DriverCreator driverCreator;
    private final AppleAuthService appleAuthService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/apple")
    @Operation(summary = "Apple Sign In 인증",
            description = "Apple에서 받은 Identity Token을 검증하고 JWT 토큰을 발급합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Apple 인증 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 - 유효하지 않은 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> appleLogin(
            @Parameter(description = "Apple 로그인 요청 데이터") @RequestBody @Valid AppleLoginRequestDTO request) {

        log.info("Apple 로그인 요청 - userId: {}", request.getUserId());

        try {
            // Apple Identity Token 검증 및 사용자 인증
            User user = appleAuthService.authenticateWithApple(request);

            // JWT 액세스 토큰 생성
            String accessToken = jwtTokenProvider.createAccessTokenFromUser(user);

            log.info("Apple 로그인 성공 - 사용자: {}, 역할: {}", user.getEmail(), user.getRole());

            // 응답 데이터 구성
            Map<String, String> responseData = new HashMap<>();
            responseData.put("token", accessToken);
            responseData.put("email", user.getEmail());
            responseData.put("name", user.getName());
            responseData.put("role", user.getRoleKey());

            return ResponseEntity.ok(new ApiResponse<>(responseData, "Apple 로그인에 성공했습니다."));

        } catch (UnauthorizedException e) {
            log.error("Apple 인증 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Apple 로그인 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, "Apple 로그인 처리 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/apple/verify")
    @Operation(summary = "Apple Identity Token 검증 (테스트용)",
            description = "Apple Identity Token의 유효성만 검증합니다. 사용자 생성은 하지 않습니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 검증 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 검증 실패")
    })
    public ResponseEntity<ApiResponse<Boolean>> verifyAppleToken(
            @Parameter(description = "Apple Identity Token") @RequestBody Map<String, String> request) {

        String identityToken = request.get("identityToken");
        log.info("Apple 토큰 검증 요청");

        try {
            boolean isValid = appleAuthService.verifyToken(identityToken);

            if (isValid) {
                return ResponseEntity.ok(new ApiResponse<>(true, "유효한 Apple Identity Token입니다."));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>(false, "유효하지 않은 토큰입니다."));
            }

        } catch (Exception e) {
            log.error("토큰 검증 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(false, "토큰 검증에 실패했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/user")
    @Operation(summary = "사용자 정보 조회",
            description = "현재 인증된 사용자의 상세 정보를 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUser(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {
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
    @Operation(summary = "로그아웃",
            description = "현재 세션을 종료하고 로그아웃 처리를 수행합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Boolean>> logout(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(new ApiResponse<>(true, "성공적으로 로그아웃을 하였습니다."));
    }

    @PostMapping("/withdrawal")
    @Operation(summary = "회원 탈퇴",
            description = "현재 인증된 사용자의 계정을 삭제하고 회원 탈퇴를 처리합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 탈퇴 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "회원 탈퇴 처리 중 오류 발생")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Boolean>> withdrawal(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response) {

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
    @Operation(summary = "사용자 권한 승급",
            description = "조직 코드를 통해 게스트 사용자를 인증된 사용자로 권한 승급합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "권한 승급 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 인증 코드"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Boolean>> rankUpUser(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "조직 인증 코드") @RequestBody CodeRequestDTO requestDTO) {

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

    @PostMapping("/upgrade-to-driver")
    @Operation(summary = "드라이버 권한 업그레이드",
            description = "사용자를 드라이버 권한으로 업그레이드하고 운전면허 정보를 등록합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "드라이버 등록 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "드라이버 등록 처리 중 오류 발생")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Driver>> upgradeToDriver(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "드라이버 업그레이드 요청 데이터") @Valid @RequestBody DriverUpgradeRequestDTO request) {

        log.info("드라이버 업그레이드 요청 - Principal: {}", principal);

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        try {
            // AuthService를 통해 사용자 정보 가져오기
            Map<String, Object> userDetails = authService.getUserDetails(principal);
            String userId = (String) userDetails.get("email");

            if (userId == null) {
                throw new UnauthorizedException("사용자 이메일을 확인할 수 없습니다");
            }

            log.info("드라이버 업그레이드 요청 - 사용자 email: {}", userId);

            // 게스트를 드라이버로 업그레이드
            Driver driver = driverCreator.upgradeGuestToDriver(userId, request);

            return ResponseEntity.ok(
                    new ApiResponse<>(driver, "드라이버 등록이 완료되었습니다.")
            );

        } catch (UnauthorizedException e) {
            log.error("인증 오류: {}", e.getMessage());
            throw e; // ExceptionHandler가 처리하도록 re-throw
        } catch (BusinessException e) {
            log.error("드라이버 업그레이드 중 비즈니스 오류 발생: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(null, e.getMessage())
            );
        } catch (Exception e) {
            log.error("드라이버 업그레이드 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ApiResponse<>(null, "드라이버 등록 처리 중 오류가 발생했습니다.")
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


    //    ✅미사용 API(프로젝트 MVP 미해당에 따른 검증절차 임시 스킵)
//    @PostMapping("/driver-verify-and-rankup")
//    @Operation(summary = "운전면허 검증 및 권한 업그레이드",
//            description = "운전면허 진위를 확인하고 드라이버 권한으로 업그레이드합니다.")
//    @ApiResponses(value = {
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운전면허 검증 및 권한 업그레이드 성공",
//                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "운전면허 검증 실패 또는 잘못된 요청"),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "처리 중 오류 발생")
//    })
//    @SecurityRequirement(name = "Bearer Authentication")
//    public ResponseEntity<ApiResponse<Boolean>> verifyDriverLicenseAndRankUp(
//            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
//            @Parameter(description = "운전면허 검증 요청 데이터") @RequestBody LicenseVerifyRequestDto requestDto) {
//
//        log.info("운전면허 검증 및 권한 업그레이드 요청");
//
//        if (principal == null) {
//            log.warn("인증된 사용자를 찾을 수 없음");
//            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
//        }
//
//        try {
//            // 1. 운전면허 검증
//            Map<String, String> verificationResult = driverService.verifyLicense(requestDto);
//
//            String authenticity = verificationResult.get("resAuthenticity");
//            if (!"1".equals(authenticity) && !"2".equals(authenticity)) {
//                log.warn("운전면허 검증 실패: {}", verificationResult.get("resAuthenticityDesc1"));
//                return ResponseEntity.badRequest().body(
//                        new ApiResponse<>(false, verificationResult.get("resAuthenticityDesc1"))
//                );
//            }
//
//            // 2. 권한 업그레이드
//            // 고정값인 organization 값 사용
//            String organizationCode = driverService.getDefaultOrganization();
//
//            boolean isRankUp = authService.rankUpGuestToDriver(principal, organizationCode, requestDto);
//
//            if (isRankUp) {
//                return ResponseEntity.ok(
//                        new ApiResponse<>(true, "운전면허 검증 및 권한 업그레이드가 완료되었습니다.")
//                );
//            } else {
//                return ResponseEntity.badRequest().body(
//                        new ApiResponse<>(false, "권한 업그레이드에 실패했습니다.")
//                );
//            }
//
//        } catch (BusinessException e) {
//            log.error("운전면허 검증 및 권한 업그레이드 중 비즈니스 오류 발생: {}", e.getMessage());
//            return ResponseEntity.badRequest().body(
//                    new ApiResponse<>(false, e.getMessage())
//            );
//        } catch (Exception e) {
//            log.error("운전면허 검증 및 권한 업그레이드 중 오류 발생", e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
//                    new ApiResponse<>(false, "처리 중 오류가 발생했습니다.")
//            );
//        }
//    }
}