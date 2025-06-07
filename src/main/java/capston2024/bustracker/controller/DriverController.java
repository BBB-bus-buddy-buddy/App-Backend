package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.domain.Driver;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.DriverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

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
    private final ObjectMapper objectMapper;
    private final AuthService authService;

    /**
     * 조직별 모든 기사 조회
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
     * 조직의 특정 기사 조회
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
     * 조직의 특정 기사 삭제
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


    //    /** ✅미사용 API(프로젝트 MVP 미해당에 따른 검증절차 임시 스킵)
//     * 운전면허 진위확인 API
//     */
//    @PostMapping("/verify")
//    @Operation(summary = "운전면허 진위확인",
//            description = "운전면허의 진위를 확인합니다.")
//    @ApiResponses(value = {
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운전면허 진위확인 성공",
//                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "진위확인 실패"),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
//    })
//    public ResponseEntity<ApiResponse<Map<String, String>>> verifyLicense(
//            @Parameter(description = "운전면허 진위확인 요청 데이터") @RequestBody LicenseVerifyRequestDto dto) {
//        log.info("운전면허 진위확인 요청 수신");
//
//        try {
//            // 요청 DTO 로깅
//            try {
//                log.info("요청 DTO: {}", objectMapper.writeValueAsString(dto));
//            } catch (Exception e) {
//                log.warn("요청 DTO 로깅 실패: {}", e.getMessage());
//            }
//
//            Map<String, String> response = driverService.verifyLicense(dto);
//
//            // 응답 로깅
//            try {
//                log.info("진위확인 응답: {}", objectMapper.writeValueAsString(response));
//            } catch (Exception e) {
//                log.warn("응답 로깅 실패: {}", e.getMessage());
//            }
//
//            // 진위확인 결과 처리 - API 가이드에 따라 "1" 또는 "2"가 성공
//            String authenticity = response.get("resAuthenticity");
//            String message;
//
//            if ("1".equals(authenticity) || "2".equals(authenticity)) {
//                message = "운전면허 진위확인에 성공했습니다: " +
//                        (response.containsKey("resAuthenticityDesc1") ?
//                                response.get("resAuthenticityDesc1") : "진위확인 완료");
//                log.info("진위확인 성공: {}", message);
//                return ResponseEntity.ok(new ApiResponse<>(response, message));
//            } else {
//                // 2단계 인증 필요 등의 특별 메시지 확인
//                if (response.containsKey("continue2Way") && "true".equals(response.get("continue2Way"))) {
//                    message = "운전면허 진위확인을 위해 추가 인증이 필요합니다.";
//                } else {
//                    message = "운전면허 진위확인에 실패했습니다: " +
//                            (response.containsKey("resAuthenticityDesc1") ?
//                                    response.get("resAuthenticityDesc1") : "진위확인 실패");
//                }
//
//                log.warn("진위확인 실패: {}", message);
//                return ResponseEntity.badRequest()
//                        .body(new ApiResponse<>(response, message));
//            }
//        } catch (BusinessException e) {
//            log.error("운전면허 진위확인 중 비즈니스 오류 발생: {}", e.getMessage(), e);
//            return ResponseEntity.badRequest()
//                    .body(new ApiResponse<>(null, e.getMessage()));
//        } catch (Exception e) {
//            log.error("운전면허 진위확인 중 예외 발생: {}", e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(new ApiResponse<>(null, "운전면허 진위확인 처리 중 오류가 발생했습니다."));
//        }
//    }
}