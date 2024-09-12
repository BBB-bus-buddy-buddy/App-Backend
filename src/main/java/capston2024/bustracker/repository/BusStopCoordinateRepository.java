package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusStopCoordinate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BusStopCoordinateRepository extends MongoRepository<BusStopCoordinate, String> {
    List<BusStopCoordinate> findByUid(String uid);
    List<BusStopCoordinate> findByUserId(String userId);
}
