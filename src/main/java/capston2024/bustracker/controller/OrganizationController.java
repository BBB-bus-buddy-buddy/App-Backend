package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.OrganizationDTO;
import capston2024.bustracker.domain.Organization;
import capston2024.bustracker.domain.auth.OrganizationIdGenerator;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organization")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "조직 관리", description = "조직(기관) 관리 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "새로운 조직 생성",
            description = "새로운 조직을 생성합니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "조직 생성 성공",
                    content = @Content(schema = @Schema(implementation = Organization.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 조직명")
    })
    public ResponseEntity<ApiResponse<Organization>> createOrganization(
            @Parameter(description = "조직 생성 정보", required = true)
            @Valid @RequestBody OrganizationDTO organizationDTO) {

        validateOrganizationName(organizationDTO.getName());

        log.info("새로운 조직 등록 요청: {}", organizationDTO.getName());

        String organizationId = OrganizationIdGenerator.generateOrganizationId(organizationDTO.getName());
        Organization createdOrganization = organizationService.generateOrganization(organizationId, organizationDTO.getName());

        log.info("조직 생성 성공 - ID: {}, 이름: {}", organizationId, organizationDTO.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(createdOrganization, "조직이 성공적으로 생성되었습니다."));
    }

    @PostMapping("/verify")
    @Operation(
            summary = "조직 코드 검증",
            description = "조직 코드의 유효성을 확인합니다. 사용자 인증 시 사용됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "유효한 조직 코드",
                    content = @Content(schema = @Schema(implementation = Organization.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "유효하지 않은 조직 코드")
    })
    public ResponseEntity<ApiResponse<Organization>> verifyOrganizationCode(
            @Parameter(description = "조직 코드 검증 요청", required = true)
            @RequestBody Map<String, String> request) {

        String code = request.get("code");

        log.info("조직 코드 검증 요청 - 코드: [{}]", code);

        if (code == null || code.trim().isEmpty()) {
            log.warn("조직 코드 검증 실패: 빈 코드");
            throw new BusinessException("조직 코드를 입력해주세요.");
        }

        try {
            Organization organization = organizationService.verifyOrganizationCode(code);

            log.info("조직 코드 검증 성공 - 조직명: [{}]", organization.getName());

            return ResponseEntity.ok(new ApiResponse<>(organization, "유효한 조직 코드입니다."));

        } catch (BusinessException e) {
            log.error("조직 코드 검증 실패 - 코드: [{}], 오류: {}", code, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("조직 코드 검증 중 시스템 오류 - 코드: [{}]", code, e);
            throw new BusinessException("조직 코드 검증 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "모든 조직 목록 조회",
            description = "시스템에 등록된 모든 조직의 목록을 조회합니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Organization.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음")
    })
    public ResponseEntity<ApiResponse<List<Organization>>> getAllOrganizations() {
        log.info("모든 조직 목록 조회 요청");

        List<Organization> organizations = organizationService.getAllOrganizations();

        log.info("조직 목록 조회 완료 - 총 {}개 조직", organizations.size());

        return ResponseEntity.ok(new ApiResponse<>(organizations, "조직 목록을 성공적으로 조회했습니다."));
    }

    @GetMapping("/{organizationId}")
    @Operation(
            summary = "특정 조직 상세 조회",
            description = "조직 ID로 특정 조직의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "조직을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<Organization>> getOrganizationById(
            @Parameter(description = "조직 ID", required = true)
            @PathVariable @NotBlank String organizationId) {

        log.info("조직 상세 조회 요청 - ID: {}", organizationId);

        Organization organization = organizationService.getOrganization(organizationId);

        log.info("조직 상세 조회 성공 - 이름: {}", organization.getName());

        return ResponseEntity.ok(new ApiResponse<>(organization, "조직 정보를 성공적으로 조회했습니다."));
    }

    @GetMapping("/check-name")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "조직명 중복 확인",
            description = "조직명이 이미 사용 중인지 확인합니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확인 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음")
    })
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkOrganizationNameAvailability(
            @Parameter(description = "확인할 조직명", required = true)
            @RequestParam @NotBlank String name) {

        log.info("조직명 중복 확인 요청: {}", name);

        boolean exists = organizationService.existByName(name);
        Map<String, Boolean> result = Map.of(
                "exists", exists,
                "available", !exists
        );

        log.info("조직명 중복 확인 결과 - 이름: {}, 존재여부: {}", name, exists);

        return ResponseEntity.ok(new ApiResponse<>(result,
                exists ? "이미 사용 중인 조직명입니다." : "사용 가능한 조직명입니다."));
    }

    /**
     * 조직명 유효성 검증
     */
    private void validateOrganizationName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("조직명은 필수입니다.");
        }

        if (name.trim().length() < 2) {
            throw new BusinessException("조직명은 최소 2글자 이상이어야 합니다.");
        }

        if (name.trim().length() > 50) {
            throw new BusinessException("조직명은 최대 50글자까지 가능합니다.");
        }
    }

    /**
     * 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());

        HttpStatus status = switch (ex.getErrorCode()) {
            case ENTITY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_ENTITY -> HttpStatus.CONFLICT;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("잘못된 인수: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, "잘못된 요청입니다: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("예기치 않은 오류 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "시스템 오류가 발생했습니다."));
    }
}