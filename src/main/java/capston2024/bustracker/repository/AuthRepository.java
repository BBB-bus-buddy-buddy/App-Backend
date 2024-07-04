package capston2024.bustracker.repository;

import capston2024.bustracker.domain.Auth;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthRepository extends MongoRepository<Auth, String> { //구글 로그인
}
