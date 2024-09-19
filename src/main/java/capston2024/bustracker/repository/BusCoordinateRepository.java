package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusCoordinate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BusCoordinateRepository extends MongoRepository<BusCoordinate, String> {
    List<BusCoordinate> findByBusIdOrderByTimestampDesc(String busId);
}
