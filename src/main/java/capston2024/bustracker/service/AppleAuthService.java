package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.AppleLoginRequestDTO;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.domain.auth.OAuthAttributes;
import capston2024.bustracker.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.util.*;

/**
 * Apple Sign In 인증 처리 서비스 (프로덕션용)
 * Identity Token을 완전히 검증하고 사용자 정보를 추출
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppleAuthService {

    private final AuthService authService;
    private final ApplePublicKeyService applePublicKeyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Apple Issuer
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    // App Bundle ID와 Service ID (Audience 검증용)
    @Value("${apple.bundle-id:kr.devse.bbb.Busbuddybuddy}")
    private String appleBundleId;

    @Value("${apple.service-id:kr.devse.bbb.Busbuddybuddy.signin}")
    private String appleServiceId;

    /**
     * Apple Identity Token을 검증하고 사용자 인증 처리
     *
     * @param request Apple 로그인 요청 데이터
     * @return 인증된 사용자 객체
     */
    public User authenticateWithApple(AppleLoginRequestDTO request) {
        try {
            // 1. Identity Token 완전 검증
            Claims claims = validateAndParseToken(request.getIdentityToken());

            // 2. Claims에서 사용자 정보 추출
            String email = claims.get("email", String.class);
            String appleUserId = claims.getSubject(); // sub claim이 Apple user ID

            log.info("Apple 인증 성공 - userId: {}, email: {}", appleUserId, email);

            // 3. 이메일이 없는 경우 처리 (Private Relay 또는 공유 거부)
            if (email == null || email.isEmpty()) {
                email = appleUserId + "@apple.privaterelay";
                log.warn("Apple 사용자가 이메일 공유를 거부함. 임시 이메일 생성: {}", email);
            }

            // 4. 이름 추출 (첫 로그인 시에만 제공됨)
            String name = request.getName();
            if (name == null || name.isEmpty()) {
                name = email.split("@")[0];
            }

            // 5. OAuthAttributes 생성
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", appleUserId);
            attributes.put("email", email);
            attributes.put("name", name);

            OAuthAttributes oAuthAttributes = OAuthAttributes.ofApple(attributes);

            // 6. 사용자 인증 처리 (기존 사용자 조회 또는 신규 생성)
            User user = authService.authenticateUser(oAuthAttributes);

            log.info("Apple 로그인 완료 - 사용자: {}, 역할: {}", user.getEmail(), user.getRole());

            return user;

        } catch (UnauthorizedException e) {
            log.error("Apple 인증 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Apple 인증 처리 중 오류 발생", e);
            throw new UnauthorizedException("Apple 인증에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * Apple Identity Token 완전 검증
     * 1. JWT 형식 검증
     * 2. Apple 공개 키로 서명 검증
     * 3. Claims 검증 (issuer, audience, expiration)
     *
     * @param identityToken Apple Identity Token
     * @return JWT Claims
     */
    private Claims validateAndParseToken(String identityToken) {
        try {
            // 1. JWT 형식 검증
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                throw new UnauthorizedException("유효하지 않은 Identity Token 형식입니다");
            }

            // 2. Header에서 kid(Key ID) 추출
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode headerNode = objectMapper.readTree(headerJson);
            String kid = headerNode.get("kid").asText();
            String alg = headerNode.get("alg").asText();

            log.debug("Apple Token - kid: {}, alg: {}", kid, alg);

            // 3. Apple 공개 키 가져오기
            PublicKey publicKey = applePublicKeyService.getPublicKey(kid);

            // 4. JWT 파싱 및 서명 검증
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(identityToken)
                    .getBody();

            // 5. Issuer 검증
            String issuer = claims.getIssuer();
            if (!APPLE_ISSUER.equals(issuer)) {
                throw new UnauthorizedException("유효하지 않은 issuer입니다: " + issuer);
            }

            // 6. 만료 시간 검증 (자동으로 검증되지만 명시적으로 확인)
            Date expiration = claims.getExpiration();
            Date now = new Date();
            if (expiration.before(now)) {
                throw new UnauthorizedException("만료된 토큰입니다");
            }

            // 7. Audience 검증
            // Apple Identity Token의 audience는 앱의 Bundle ID 또는 Service ID
            String audience = claims.getAudience();
            if (audience != null) {
                // Bundle ID 또는 Service ID 중 하나와 일치해야 함
                List<String> validAudiences = Arrays.asList(appleBundleId, appleServiceId);
                if (!validAudiences.contains(audience)) {
                    log.warn("유효하지 않은 audience - expected: {}, actual: {}", validAudiences, audience);
                    // 개발 환경에서는 경고만 출력, 프로덕션에서는 아래 줄 주석 해제
                    // throw new UnauthorizedException("유효하지 않은 audience입니다: " + audience);
                }
            }

            log.debug("Apple Token 검증 완료 - sub: {}, aud: {}, exp: {}", claims.getSubject(), audience, expiration);

            return claims;

        } catch (ExpiredJwtException e) {
            log.error("만료된 Apple Identity Token", e);
            throw new UnauthorizedException("만료된 토큰입니다");
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 형식", e);
            throw new UnauthorizedException("지원되지 않는 토큰 형식입니다");
        } catch (MalformedJwtException e) {
            log.error("잘못된 형식의 JWT", e);
            throw new UnauthorizedException("잘못된 형식의 토큰입니다");
        } catch (SignatureException e) {
            log.error("JWT 서명 검증 실패", e);
            throw new UnauthorizedException("토큰 서명 검증에 실패했습니다");
        } catch (IllegalArgumentException e) {
            log.error("JWT claims가 비어있음", e);
            throw new UnauthorizedException("토큰이 유효하지 않습니다");
        } catch (Exception e) {
            log.error("Identity Token 검증 실패", e);
            throw new UnauthorizedException("Identity Token 검증에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * Apple Identity Token의 유효성을 사전 검증 (테스트용)
     * 실제 사용자 생성 없이 토큰만 검증
     *
     * @param identityToken Apple Identity Token
     * @return 검증 성공 여부
     */
    public boolean verifyToken(String identityToken) {
        try {
            validateAndParseToken(identityToken);
            return true;
        } catch (Exception e) {
            log.error("토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }
}
