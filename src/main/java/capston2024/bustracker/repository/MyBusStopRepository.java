package capston2024.bustracker.repository;

import capston2024.bustracker.domain.Auth;
import capston2024.bustracker.domain.BusStopCoordinate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MyBusStopRepository extends MongoRepository<BusStopCoordinate, String> {
}
