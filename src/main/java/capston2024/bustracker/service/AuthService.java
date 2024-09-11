package capston2024.bustracker.service;

import capston2024.bustracker.config.auth.dto.OAuthAttributes;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;

@Service
@Slf4j
public class AuthService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    // Google OAuth 토큰을 검증하고 사용자 정보를 OAuthAttributes로 반환합니다.
    public OAuthAttributes authenticate(String token) {
        return extractUserInfoFromToken(token);
    }

    // 토큰에서 Google 사용자 정보를 추출하고 OAuthAttributes로 변환합니다.
    private OAuthAttributes extractUserInfoFromToken(String token) {
        try {
            log.info("token : {}", token);
            GoogleIdTokenVerifier verifier = createGoogleIdTokenVerifier();
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            Map<String, Object> attributes = payload.getUnknownKeys();

            // Google 사용자 정보를 OAuthAttributes로 변환
            return OAuthAttributes.ofGoogle("sub", attributes);

        } catch (IOException | GeneralSecurityException e) {
            log.error("Error verifying token: ", e);
            throw new RuntimeException("Failed to verify Google token", e);
        }
    }

    // Google Id 토큰 검증기
    private GoogleIdTokenVerifier createGoogleIdTokenVerifier() {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }
}
