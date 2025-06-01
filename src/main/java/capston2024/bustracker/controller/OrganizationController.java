package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.OrganizationDTO;
import capston2024.bustracker.domain.Organization;
import capston2024.bustracker.domain.auth.OrganizationIdGenerator;
import capston2024.bustracker.service.OrganizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Swagger 어노테이션 추가
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/organization")
@Slf4j
@Tag(name = "Organization", description = "조직 관리 관련 API")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /**
     * 새로운 조직 생성
     */
    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "조직 생성",
            description = "새로운 조직을 생성합니다. 총관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조직 생성 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (총관리자 권한 필요)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 조직명")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Organization>> generateOrganization(
            @Parameter(description = "조직 생성 요청 데이터") @RequestBody OrganizationDTO organizationDTO) {

        log.info("새로운 조직 등록 요청: {}", organizationDTO.getName());

        String organizationId = OrganizationIdGenerator.generateOrganizationId(organizationDTO.getName());
        Organization isGenerated = organizationService.generateOrganization(organizationId, organizationDTO.getName());

        return ResponseEntity.ok(new ApiResponse<>(isGenerated, "성공적으로 조직이 생성되었습니다."));
    }

    /**
     * 조직 코드 유효성 검증
     */
    @PostMapping("/verify")
    @Operation(summary = "조직 코드 검증",
            description = "조직 코드의 유효성을 확인합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조직 코드 검증 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "유효하지 않은 조직 코드")
    })
    public ResponseEntity<ApiResponse<Organization>> verifyOrganizationCode(
            @Parameter(description = "조직 코드 검증 요청", example = "{\"code\": \"ORG12345\"}") @RequestBody Map<String, String> request) {

        String code = request.get("code");
        log.info("==================================================");
        log.info("API 요청: 조직 코드 검증 - 코드: [{}]", code);

        try {
            Organization organization = organizationService.verifyOrganizationCode(code);

            log.info("API 응답: 조직 코드 검증 성공 - 조직명: [{}]", organization.getName());
            log.info("==================================================");

            return ResponseEntity.ok(new ApiResponse<>(
                    organization,
                    "유효한 조직 코드입니다."
            ));
        } catch (Exception e) {
            log.error("API 응답: 조직 코드 검증 실패 - 오류: {}", e.getMessage());
            log.info("==================================================");
            throw e; // 전역 예외 핸들러가 처리하도록 다시 throw
        }
    }

    /**
     * 모든 조직 목록 조회
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "모든 조직 목록 조회",
            description = "시스템에 등록된 모든 조직의 목록을 조회합니다. 총관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조직 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (총관리자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<java.util.List<Organization>>> getAllOrganizations() {
        log.info("모든 조직 목록 조회 요청");
        java.util.List<Organization> organizations = organizationService.getAllOrganizations();
        return ResponseEntity.ok(new ApiResponse<>(organizations, "조직 목록을 성공적으로 조회했습니다."));
    }
}