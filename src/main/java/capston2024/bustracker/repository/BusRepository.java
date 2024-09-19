package capston2024.bustracker.repository;

import capston2024.bustracker.domain.Bus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BusRepository extends MongoRepository<Bus, String> {
    Optional<Bus> findByIdOrderByTimestampDesc(String id); // 버스 아이디로 버스 조회
}
