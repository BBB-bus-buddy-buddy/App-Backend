package capston2024.bustracker.repository;

import capston2024.bustracker.domain.Bus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusRepository extends MongoRepository<Bus, String> {

    // 기본 조회 메서드들
    Optional<Bus> findByBusNumber(String busNumber);
    boolean existsByBusNumber(String busNumber);

    // 조직 ID로 버스 조회
    List<Bus> findByOrganizationId(String organizationId);

    // 버스 번호와 조직 ID로 버스 조회
    Optional<Bus> findByBusNumberAndOrganizationId(String busNumber, String organizationId);

    // 실제 버스 번호와 조직 ID로 버스 조회
    Optional<Bus> findByBusRealNumberAndOrganizationId(String busRealNumber, String organizationId);

    // 실제 버스 번호 중복 체크 (조직 내)
    boolean existsByBusRealNumberAndOrganizationId(String busRealNumber, String organizationId);

    // 운영 상태별 버스 조회
    List<Bus> findByOrganizationIdAndOperationalStatus(String organizationId, Bus.OperationalStatus operationalStatus);

    // 서비스 상태별 버스 조회
    List<Bus> findByOrganizationIdAndServiceStatus(String organizationId, Bus.ServiceStatus serviceStatus);

    // 운영 가능한 버스 조회 (ACTIVE 상태)
    List<Bus> findByOrganizationIdAndOperationalStatusAndServiceStatus(
            String organizationId, Bus.OperationalStatus operationalStatus, Bus.ServiceStatus serviceStatus);

    // 라우트 ID로 버스 조회
    @Query("{'routeId.$id': ?0}")
    List<Bus> findByRouteId(String routeId);

    // 운영 가능한 버스만 조회
    List<Bus> findByOrganizationIdAndOperationalStatusIn(String organizationId, List<Bus.OperationalStatus> statuses);
}