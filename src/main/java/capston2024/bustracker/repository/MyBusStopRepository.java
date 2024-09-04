package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusStopCoordinate;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MyBusStopRepository extends MongoRepository<BusStopCoordinate, String> {
}
