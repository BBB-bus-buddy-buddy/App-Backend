package capston2024.bustracker.domain.auth;

import capston2024.bustracker.config.dto.DriverUpgradeRequestDTO;
import capston2024.bustracker.domain.Driver;
import capston2024.bustracker.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverCreator {

    private final MongoTemplate mongoTemplate;

    @Transactional
    public Driver upgradeGuestToDriver(String userEmail, DriverUpgradeRequestDTO request) throws Exception {
        try {
            log.info("드라이버 업그레이드 시작 - 이메일: {}", userEmail);

            // 이메일로 사용자 검증 (email 필드 사용)
            Query query = new Query(Criteria.where("email").is(userEmail).and("role").is("GUEST"));
            User existingUser = mongoTemplate.findOne(query, User.class, "Auth");

            if (existingUser == null) {
                log.error("사용자를 찾을 수 없음 - 이메일: {}, 또는 이미 드라이버로 등록됨", userEmail);
                throw new Exception("사용자를 찾을 수 없거나 이미 드라이버로 등록되어 있습니다.");
            }

            log.info("사용자 찾음 - ID: {}, 이메일: {}, 현재 역할: {}",
                    existingUser.getId(), existingUser.getEmail(), existingUser.getRole());

            // 2. 조직 코드 검증
            if (!isValidOrganizationCode(request.getOrganizationId())) {
                throw new Exception("유효하지 않은 조직 코드입니다.");
            }

            // 3. 사용자를 드라이버로 업데이트 (email로 찾아서 업데이트)
            Update update = new Update()
                    .set("role", "DRIVER")
                    .set("organizationId", request.getOrganizationId())
                    .set("identity", request.getIdentity())
                    .set("birthDate", request.getBirthDate())
                    .set("phoneNumber", request.getPhoneNumber())
                    .set("licenseNumber", request.getLicenseNumber())
                    .set("licenseSerial", request.getLicenseSerial())
                    .set("licenseType", request.getLicenseType())
                    .set("licenseExpiryDate", request.getLicenseExpiryDate())
                    .unset("myStations");

            mongoTemplate.updateFirst(query, update, "Auth");

            // 4. 업데이트된 드라이버 조회 (ObjectId로 조회)
            Driver updatedDriver = mongoTemplate.findById(existingUser.getId(), Driver.class, "Auth");

            if (updatedDriver == null) {
                throw new Exception("드라이버 업그레이드에 실패했습니다.");
            }

            log.info("드라이버 업그레이드 성공 - 이메일: {}, ID: {}", userEmail, updatedDriver.getId());
            return updatedDriver;

        } catch (Exception e) {
            log.error("드라이버 업그레이드 실패 - 이메일: {}, 오류: {}", userEmail, e.getMessage());
            throw e;
        }
    }

    private boolean isValidOrganizationCode(String organizationId) {
        // TODO: Implement actual organization code validation logic
        // For now, just check if it's not empty
        return organizationId != null && !organizationId.trim().isEmpty();
    }
}