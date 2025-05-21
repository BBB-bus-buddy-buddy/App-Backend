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

@RestController
@RequestMapping("/api/organization")
@Slf4j
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Organization>> generateOrganization(@RequestBody OrganizationDTO organizationDTO) {
        log.info("새로운 조직 등록 요청: {}", organizationDTO.getName());
        String organizationId = OrganizationIdGenerator.generateOrganizationId(organizationDTO.getName());
        Organization isGenerated = organizationService.generateOrganization(organizationId, organizationDTO.getName());
        return ResponseEntity.ok(new ApiResponse<>(isGenerated, "성공적으로 조직이 생성되었습니다."));
    }

    /**
     * 조직 코드의 유효성을 확인하는 API
     *
     * @param request 조직 코드를 포함한 요청 데이터
     * @return 조직 코드 유효성 검증 결과
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Organization>> verifyOrganizationCode(@RequestBody Map<String, String> request) {
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
