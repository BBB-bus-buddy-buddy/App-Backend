package capston2024.bustracker.repository;

import capston2024.bustracker.domain.Bus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusRepository extends MongoRepository<Bus, String> {
    Optional<Bus> findByIdOrderByTimestampDesc(String id); // 버스 아이디로 버스 조회
    Optional<Bus> findBusByBusNumber(String busNumber);
    boolean existsBusByBusNumber(String busNumber); // 버스 번호 중복 감지

    // 조직 ID로 버스 조회
    List<Bus> findByOrganizationId(String organizationId);

    // 버스 번호와 조직 ID로 버스 조회
    Optional<Bus> findByBusNumberAndOrganizationId(String busNumber, String organizationId);

    // 라우트 ID로 버스 조회
    @Query("{'routeId.$id': ?0}")
    List<Bus> findByRouteId(String routeId);

    // 특정 정류장을 경유하는 버스 조회 (RouteId를 통해 외부에서 구현)
    // 기존 findByStationsContaining(Station station) 삭제
}