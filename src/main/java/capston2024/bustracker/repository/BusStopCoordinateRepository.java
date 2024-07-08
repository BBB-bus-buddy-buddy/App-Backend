package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusStopCoordinate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BusStopCoordinateRepository extends MongoRepository<BusStopCoordinate, String> {
}
