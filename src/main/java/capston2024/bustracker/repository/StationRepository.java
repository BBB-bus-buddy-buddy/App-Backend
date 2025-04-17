package capston2024.bustracker.repository;

import capston2024.bustracker.domain.Station;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface StationRepository extends MongoRepository<Station, String> {
    // 특정 정류장을 정류장 이름으로 조회
    Optional<Station> findByName(String name);

    List<Station> findAllByOrganizationId(String organizationId);

    // 특정 이름이 포함된 정류장 조회
    List<Station> findByNameAndOrganizationId(String stationName, String organizationId);
}
