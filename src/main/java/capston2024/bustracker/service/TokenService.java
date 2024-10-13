package capston2024.bustracker.service;

import capston2024.bustracker.domain.auth.TokenInfo;
import capston2024.bustracker.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TokenService {
    private final TokenRepository tokenRepository;

    public void saveToken(String username, String refreshToken, String accessToken) {
        TokenInfo tokenInfo = new TokenInfo(username, refreshToken, accessToken);
        tokenRepository.save(tokenInfo);
    }

    public TokenInfo findByAccessToken(String accessToken) {
        return tokenRepository.findByAccessToken(accessToken).orElse(null);
    }

    public TokenInfo findByUserName(String username){
        Optional<TokenInfo> token = tokenRepository.findByUsername(username);
        return token.orElse(null);
    }
    public void updateAccessToken(String username, String newAccessToken) {
        tokenRepository.findByUsername(username).ifPresent(tokenInfo -> {
            tokenInfo.setAccessToken(newAccessToken);
        });
    }
}