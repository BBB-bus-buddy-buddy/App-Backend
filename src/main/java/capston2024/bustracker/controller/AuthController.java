package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.CodeRequestDTO;
import capston2024.bustracker.config.dto.DriverUpgradeRequestDTO;
import capston2024.bustracker.config.dto.LicenseVerifyRequestDto;
import capston2024.bustracker.domain.Driver;
import capston2024.bustracker.domain.auth.DriverCreator;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "인증 관리", description = "사용자 인증, 로그인/로그아웃, 권한 승급 등을 관리하는 API")
public class AuthController {

    private final AuthService authService;
    private final DriverService driverService;
    private final DriverCreator driverCreator;

    @GetMapping("/user")
    @Operation(
            summary = "현재 로그인한 사용자 정보 조회",
            description = "JWT 토큰을 통해 인증된 현재 사용자의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
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
    @Operation(
            summary = "로그아웃",
            description = "현재 로그인한 사용자를 로그아웃 처리합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
    })
    public ResponseEntity<ApiResponse<Boolean>> logout(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(new ApiResponse<>(true, "성공적으로 로그아웃을 하였습니다."));
    }

    @PostMapping("/withdrawal")
    @Operation(
            summary = "회원 탈퇴",
            description = "현재 로그인한 사용자의 회원 탈퇴를 처리합니다. 권한이 GUEST로 변경되고 조직 정보가 초기화됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "처리 중 오류")
    })
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
            authService.logout(request, response);
            return ResponseEntity.ok(new ApiResponse<>(true, "회원탈퇴가 성공적으로 처리되었습니다."));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "회원탈퇴 처리 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/rankUp")
    @Operation(
            summary = "일반 사용자 권한 승급",
            description = "GUEST 권한을 가진 사용자가 조직 코드를 입력하여 USER 권한으로 승급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "승급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 인증 코드"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<Boolean>> rankUpUser(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "조직 인증 코드", required = true)
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

    @PostMapping("/upgrade-to-driver")
    @Operation(
            summary = "운전자 권한 업그레이드",
            description = "일반 사용자가 운전면허 정보를 제출하여 DRIVER 권한으로 업그레이드합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업그레이드 성공",
                    content = @Content(schema = @Schema(implementation = Driver.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<Driver>> upgradeToDriver(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "운전자 업그레이드 정보", required = true)
            @Valid @RequestBody DriverUpgradeRequestDTO request) {

        log.info("드라이버 업그레이드 요청 - Principal: {}", principal);

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        try {
            Map<String, Object> userDetails = authService.getUserDetails(principal);
            String userId = (String) userDetails.get("email");

            if (userId == null) {
                throw new UnauthorizedException("사용자 이메일을 확인할 수 없습니다");
            }

            log.info("드라이버 업그레이드 요청 - 사용자 email: {}", userId);

            Driver driver = driverCreator.upgradeGuestToDriver(userId, request);

            return ResponseEntity.ok(
                    new ApiResponse<>(driver, "드라이버 등록이 완료되었습니다.")
            );

        } catch (UnauthorizedException e) {
            log.error("인증 오류: {}", e.getMessage());
            throw e;
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

    @PostMapping("/driver-verify-and-rankup")
    @Operation(
            summary = "운전면허 검증 및 드라이버 권한 승급",
            description = "운전면허 진위를 확인하고 검증에 성공하면 자동으로 DRIVER 권한으로 승급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검증 및 승급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검증 실패 또는 잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<Boolean>> verifyDriverLicenseAndRankUp(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "운전면허 검증 정보", required = true)
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
}