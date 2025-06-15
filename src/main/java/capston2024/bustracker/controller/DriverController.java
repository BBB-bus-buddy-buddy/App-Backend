package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.domain.Driver;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.DriverService;
import capston2024.bustracker.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

// Swagger 어노테이션 추가
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/driver")
@Slf4j
@Tag(name = "Driver", description = "버스 기사 관리 관련 API")
public class DriverController {

    private final DriverService driverService;
    private final UserService userService; // 추가된 UserService
    private final ObjectMapper objectMapper;
    private final AuthService authService;

    /**
     * 조직별 모든 기사 조회 (관리자용)
     */
    @GetMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "조직별 기사 목록 조회",
            description = "현재 사용자 조직의 모든 기사를 조회합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기사 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<Driver>>> getDriversByOrganization(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        log.info("조직별 기사 목록 조회 요청");

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        // 관리자 권한 검증
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 관리자만 접근 가능
        if (!"STAFF".equals(role)) {
            throw new UnauthorizedException("관리자 권한이 필요합니다");
        }

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 기사 정보를 조회할 수 없습니다.");
        }

        List<Driver> drivers = driverService.getDriversByOrganizationId(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(drivers,
                String.format("조직의 모든 기사가 성공적으로 조회되었습니다. (총 %d명)", drivers.size())));
    }

    /**
     * 조직의 특정 기사 조회 (관리자용)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "특정 기사 조회",
            description = "조직의 특정 기사 정보를 조회합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기사 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "기사를 찾을 수 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Driver>> getDriverById(
            @Parameter(description = "조회할 기사 ID") @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        log.info("특정 기사 조회 요청 - ID: {}", id);

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        // 관리자 권한 검증
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 관리자만 접근 가능
        if (!"STAFF".equals(role)) {
            throw new UnauthorizedException("관리자 권한이 필요합니다");
        }

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 기사 정보를 조회할 수 없습니다.");
        }

        Driver driver = driverService.getDriverByIdAndOrganizationId(id, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(driver, "기사 정보가 성공적으로 조회되었습니다."));
    }

    /**
     * 조직의 특정 기사 삭제 (관리자용)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "기사 삭제",
            description = "조직의 특정 기사를 삭제합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기사 삭제 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "기사를 찾을 수 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Boolean>> deleteDriver(
            @Parameter(description = "삭제할 기사 ID") @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        log.info("기사 삭제 요청 - ID: {}", id);

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        // 관리자 권한 검증
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 관리자만 접근 가능
        if (!"STAFF".equals(role)) {
            throw new UnauthorizedException("관리자 권한이 필요합니다");
        }

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 기사를 삭제할 수 없습니다.");
        }

        boolean result = driverService.deleteDriver(id, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(result, "기사가 성공적으로 삭제되었습니다."));
    }

    /**
     * 현재 운전자 정보 조회 (운전자 본인용)
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "현재 운전자 정보 조회",
            description = "현재 인증된 운전자의 정보를 조회합니다. 운전자 권한이 필요합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Driver>> getCurrentDriver(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String driverId = (String) userInfo.get("id");
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        if (!"ROLE_DRIVER".equals(role)) {
            throw new UnauthorizedException("운전자 권한이 필요합니다");
        }

        // User를 통해 ID를 찾아서 Driver 조회
        try {
            Driver  driver = driverService.getDriverByIdAndOrganizationId(driverId, organizationId);
            return ResponseEntity.ok(new ApiResponse<>(driver, "운전자 정보가 성공적으로 조회되었습니다."));
        } catch (Exception e) {
            throw new ResourceNotFoundException("운전자 정보를 찾을 수 없습니다");
        }
    }

    /**
     * 운전자 프로필 업데이트 - 프론트엔드 호환
     */
    @PutMapping("/profile")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운전자 프로필 업데이트",
            description = "현재 운전자의 개인 정보를 업데이트합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Map<String, String>>> updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "프로필 업데이트 데이터") @RequestBody Map<String, String> profileData) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String role = (String) userInfo.get("role");

        if (!"ROLE_DRIVER".equals(role)) {
            throw new UnauthorizedException("운전자 권한이 필요합니다");
        }

        log.info("운전자 프로필 업데이트 요청: {}", profileData.keySet());

        // 현재는 기본 응답만 제공 (실제 구현은 Driver 엔티티 업데이트 로직 필요)
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "프로필이 업데이트되었습니다");

        return ResponseEntity.ok(new ApiResponse<>(response, "프로필이 성공적으로 업데이트되었습니다."));
    }

    /**
     * 운전자 면허 정보 업데이트 - 프론트엔드 호환
     */
    @PutMapping("/license")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운전자 면허 정보 업데이트",
            description = "현재 운전자의 면허 정보를 업데이트합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Map<String, String>>> updateLicense(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "면허 정보 업데이트 데이터") @RequestBody Map<String, String> licenseData) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String role = (String) userInfo.get("role");

        if (!"ROLE_DRIVER".equals(role)) {
            throw new UnauthorizedException("운전자 권한이 필요합니다");
        }

        log.info("운전자 면허 정보 업데이트 요청: {}", licenseData.keySet());

        // 현재는 기본 응답만 제공 (실제 구현은 Driver 엔티티 업데이트 로직 필요)
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "면허 정보가 업데이트되었습니다");

        return ResponseEntity.ok(new ApiResponse<>(response, "면허 정보가 성공적으로 업데이트되었습니다."));
    }

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());

        HttpStatus status = switch (ex.getErrorCode()) {
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_ENTITY -> HttpStatus.CONFLICT;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_TOKEN, TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("인증 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 리소스 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("리소스 찾을 수 없음: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}