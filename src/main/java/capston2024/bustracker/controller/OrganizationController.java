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
    public ResponseEntity<ApiResponse<Organization>> generateOrganization(@RequestBody OrganizationDTO organizationDTO){
        log.info("새로운 조직 등록 요청: {}", organizationDTO.getName());
        String organizationId = OrganizationIdGenerator.generateOrganizationId(organizationDTO.getName());
        Organization isGenerated = organizationService.generateOrganization(organizationId, organizationDTO.getName());
        return ResponseEntity.ok(new ApiResponse<>(isGenerated, "성공적으로 조직이 생성되었습니다."));
    }

}
