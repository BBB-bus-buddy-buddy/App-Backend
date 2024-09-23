package capston2024.bustracker.repository;

import capston2024.bustracker.domain.School;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SchoolRepository extends MongoRepository<School, String> {
    Optional<School> findByName(String name);

    boolean existsByName(String name);
}
