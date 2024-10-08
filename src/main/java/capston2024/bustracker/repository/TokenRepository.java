package capston2024.bustracker.repository;

import capston2024.bustracker.domain.auth.TokenInfo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TokenRepository extends MongoRepository<TokenInfo, String> {
    Optional<TokenInfo> findByAccessToken(String accessToken);
    Optional<TokenInfo> findByUsername(String username);
}
