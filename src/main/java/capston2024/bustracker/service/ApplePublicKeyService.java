package capston2024.bustracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apple 공개 키를 가져오고 캐싱하는 서비스
 * Apple의 JWK(JSON Web Key) 엔드포인트에서 공개 키를 가져와서
 * Identity Token의 서명을 검증하는데 사용
 */
@Service
@Slf4j
public class ApplePublicKeyService {

    private static final String APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys";

    // 공개 키 캐시 (kid -> PublicKey)
    private final Map<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();

    // 마지막 키 갱신 시간
    private long lastRefreshTime = 0;

    // 캐시 유효 시간 (1시간)
    private static final long CACHE_DURATION_MS = 60 * 60 * 1000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ApplePublicKeyService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * kid(Key ID)에 해당하는 Apple 공개 키 가져오기
     * 캐시에 없거나 만료된 경우 Apple 서버에서 최신 키 가져옴
     *
     * @param kid JWT 헤더의 kid (Key ID)
     * @return RSA Public Key
     * @throws Exception 공개 키를 가져올 수 없는 경우
     */
    public PublicKey getPublicKey(String kid) throws Exception {
        // 캐시 확인
        if (isCacheValid() && publicKeyCache.containsKey(kid)) {
            log.debug("캐시에서 공개 키 조회 - kid: {}", kid);
            return publicKeyCache.get(kid);
        }

        // 캐시가 없거나 만료된 경우 새로 가져오기
        log.info("Apple 공개 키 새로 가져오기 - kid: {}", kid);
        refreshPublicKeys();

        PublicKey publicKey = publicKeyCache.get(kid);
        if (publicKey == null) {
            throw new Exception("해당 kid에 대한 공개 키를 찾을 수 없습니다: " + kid);
        }

        return publicKey;
    }

    /**
     * Apple 서버에서 공개 키 목록을 가져와서 캐시 갱신
     */
    private synchronized void refreshPublicKeys() throws Exception {
        try {
            log.info("Apple 공개 키 엔드포인트 호출: {}", APPLE_PUBLIC_KEYS_URL);

            // Apple JWK 엔드포인트 호출
            String response = restTemplate.getForObject(APPLE_PUBLIC_KEYS_URL, String.class);
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode keysNode = rootNode.get("keys");

            if (keysNode == null || !keysNode.isArray()) {
                throw new Exception("Apple 공개 키 응답 형식이 올바르지 않습니다");
            }

            // 기존 캐시 초기화
            publicKeyCache.clear();

            // 각 키를 파싱하여 캐시에 저장
            for (JsonNode keyNode : keysNode) {
                String kid = keyNode.get("kid").asText();
                String kty = keyNode.get("kty").asText(); // "RSA"
                String alg = keyNode.get("alg").asText(); // "RS256"
                String use = keyNode.get("use").asText(); // "sig"
                String n = keyNode.get("n").asText();     // modulus
                String e = keyNode.get("e").asText();     // exponent

                if ("RSA".equals(kty) && "sig".equals(use)) {
                    PublicKey publicKey = createPublicKey(n, e);
                    publicKeyCache.put(kid, publicKey);
                    log.info("공개 키 캐시 추가 - kid: {}, alg: {}", kid, alg);
                }
            }

            lastRefreshTime = System.currentTimeMillis();
            log.info("Apple 공개 키 캐시 갱신 완료 - 총 {}개", publicKeyCache.size());

        } catch (Exception e) {
            log.error("Apple 공개 키 가져오기 실패", e);
            throw new Exception("Apple 공개 키를 가져올 수 없습니다: " + e.getMessage(), e);
        }
    }

    /**
     * JWK의 n(modulus)과 e(exponent)로부터 RSA PublicKey 생성
     */
    private PublicKey createPublicKey(String nStr, String eStr) throws Exception {
        // Base64 URL 디코딩
        byte[] nBytes = Base64.getUrlDecoder().decode(nStr);
        byte[] eBytes = Base64.getUrlDecoder().decode(eStr);

        // BigInteger로 변환
        BigInteger n = new BigInteger(1, nBytes);
        BigInteger e = new BigInteger(1, eBytes);

        // RSA Public Key 생성
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(n, e);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(publicKeySpec);
    }

    /**
     * 캐시가 유효한지 확인 (1시간 이내)
     */
    private boolean isCacheValid() {
        return !publicKeyCache.isEmpty()
            && (System.currentTimeMillis() - lastRefreshTime) < CACHE_DURATION_MS;
    }

    /**
     * 캐시 강제 초기화 (테스트 또는 관리 용도)
     */
    public void clearCache() {
        log.info("Apple 공개 키 캐시 초기화");
        publicKeyCache.clear();
        lastRefreshTime = 0;
    }
}
