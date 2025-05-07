package capston2024.bustracker.service;

import capston2024.bustracker.domain.Organization;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.DuplicateResourceException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.repository.OrganizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /**
     * OrganizationId로 조직을 찾는 메소드
     * @param organizationId 조직 ID
     * @return 찾은 조직 엔티티
     */
    public Organization getOrganization(String organizationId) {
        log.info("ID {}로 조직(Organization)을 찾는 중입니다....", organizationId);
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "해당 조직 코드를 찾을 수 없습니다."));
    }

    // OrganizationService.java에 추가할 메서드
    /**
     * 모든 조직 목록 조회
     */
    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    /**
     * 이름으로 조직이 존재하는지 확인하는 메소드
     * @param name 조직 이름
     * @return 조직 존재 여부
     */
    public boolean existByName(String name) {
        // Optional을 활용하여 이름으로 조직이 존재하는지 확인
        return organizationRepository.findByName(name).isPresent();
    }

    /**
     * 새로운 조직을 생성하는 메소드
     * @param organizationId 조직 ID
     * @param name 조직 이름
     * @return 생성된 조직 엔티티
     */
    public Organization generateOrganization(String organizationId, String name) {
        // 조직 이름이 이미 존재하는지 확인
        boolean isExistName = existByName(name);

        // 조직 이름이 이미 존재하면 예외 발생
        if(isExistName) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "이미 존재하는 조직 이름입니다.");
        }

        // 조직 이름이 존재하지 않으면 새로운 조직 생성
        Organization newOrganization = Organization.builder()
                .id(organizationId)
                .name(name)
                .build();

        // 생성된 조직 저장 후 반환
        return organizationRepository.save(newOrganization);
    }
}
