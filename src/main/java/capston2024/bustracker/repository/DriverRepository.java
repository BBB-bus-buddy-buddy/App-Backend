package capston2024.bustracker.repository;

import capston2024.bustracker.domain.Driver;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Driver 엔티티를 위한 Repository
 * Auth 컬렉션에서 role이 'DRIVER'인 문서만 조회
 */
@Repository
public interface DriverRepository extends MongoRepository<Driver, String> {

    /**
     * 이메일로 드라이버 조회
     */
    @Query("{'email': ?0, 'role': 'DRIVER'}")
    Optional<Driver> findByEmail(String email);

    /**
     * 조직별 모든 드라이버 조회
     */
    @Query("{'organizationId': ?0, 'role': 'DRIVER'}")
    List<Driver> findByOrganizationId(String organizationId);

    /**
     * ID와 조직으로 드라이버 조회
     */
    @Query("{'_id': ?0, 'organizationId': ?1, 'role': 'DRIVER'}")
    Optional<Driver> findByIdAndOrganizationId(String id, String organizationId);

    /**
     * 운전면허 번호로 드라이버 조회
     */
    @Query("{'licenseNumber': ?0, 'role': 'DRIVER'}")
    Optional<Driver> findByLicenseNumber(String licenseNumber);

    /**
     * 조직과 운전면허 번호로 드라이버 존재 여부 확인
     */
    @Query(value = "{'organizationId': ?0, 'licenseNumber': ?1, 'role': 'DRIVER'}", exists = true)
    boolean existsByOrganizationIdAndLicenseNumber(String organizationId, String licenseNumber);
}