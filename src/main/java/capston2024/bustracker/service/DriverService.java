package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.LicenseVerifyRequestDto;
import capston2024.bustracker.config.status.Role;
import capston2024.bustracker.domain.Driver;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.repository.DriverRepository;
import capston2024.bustracker.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 운전면허 관련 비즈니스 로직을 처리하는 서비스
 */
@Service
@Slf4j
public class DriverService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DriverRepository driverRepository;
    private final UserRepository userRepository;

    // API 접근 정보 (상수화)
    private static final String CLIENT_ID = "55a49e2c-2f9a-412d-9e54-02842fcc9100";
    private static final String CLIENT_SECRET = "999409db-643c-492d-91f3-e5e64cf7808c";
    private static final String ORGANIZATION = "0001";
    private static final String LOGIN_TYPE = "5"; // 간편인증
    private static final String API_ENDPOINT = "https://development.codef.io/v1/kr/public/ef/driver-license/status";
    private static final String TOKEN_URL = "https://oauth.codef.io/oauth/token?grant_type=client_credentials&scope=read";

    // RestTemplate with custom error handler
    private final RestTemplate restTemplate;

    @Autowired
    public DriverService(DriverRepository driverRepository, UserRepository userRepository) {
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;

        // 에러 응답을 직접 처리하기 위한 커스텀 에러 핸들러
        this.restTemplate = new RestTemplate();
        this.restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) throws IOException {
                return false; // 에러를 직접 처리하기 위해 항상 false 반환
            }

            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) throws IOException {
                // 에러 처리는 서비스에서 수행
            }
        });
    }

    /**
     * 운전면허 진위확인 처리
     */
    public Map<String, String> verifyLicense(LicenseVerifyRequestDto dto) {
        log.info("운전면허 진위확인 요청 시작");

        try {
            // 요청 DTO 로깅
            log.info("요청 DTO 정보: {}", toJsonString(dto));

            // 토큰 발급
            String token = getAccessToken();
            log.info("발급된 액세스 토큰: {}", maskToken(token));

            // 기본 필드 설정
            dto.setOrganization(ORGANIZATION);
            dto.setLoginType(LOGIN_TYPE);

            // 1단계: 첫 번째 API 호출 (진위확인 요청)
            Map<String, Object> firstRequestBody = createFirstRequestBody(dto);
            Map<String, Object> firstResponse = callDriverLicenseAPI(token, firstRequestBody);

            // 2단계: 필요시 두 번째 API 호출 (간편인증 승인)
            if (isTwoWayAuthRequired(firstResponse)) {
                log.info("2단계 인증 필요: 추가 정보 요청");
                Map<String, Object> secondRequestBody = createSecondRequestBody(dto, firstRequestBody, firstResponse);
                Map<String, Object> secondResponse = callDriverLicenseAPI(token, secondRequestBody);
                return processSecondResponse(secondResponse);
            } else {
                log.warn("2단계 인증 프로세스를 시작할 수 없습니다. 첫 번째 응답 처리");
                return processFirstResponse(firstResponse);
            }
        } catch (Exception e) {
            log.error("운전면허 진위확인 처리 중 예외 발생", e);
            throw new BusinessException("운전면허 진위확인 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 조직별 모든 기사 조회
     */
    public List<Driver> getDriversByOrganizationId(String organizationId) {
        log.info("조직 ID {}의 모든 기사 조회", organizationId);
        return driverRepository.findByOrganizationId(organizationId);
    }

    /**
     * 조직의 특정 기사 조회
     */
    public Driver getDriverByIdAndOrganizationId(String driverId, String organizationId) {
        log.info("조직 ID {}의 기사 ID {} 조회", organizationId, driverId);

        Driver driver = driverRepository.findByIdAndOrganizationId(driverId, organizationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "해당 기사를 찾을 수 없습니다."));

        return driver;
    }

    /**
     * 기사 삭제 (권한을 GUEST로 변경)
     */
    @Transactional
    public boolean deleteDriver(String driverId, String organizationId) {
        log.info("조직 ID {}의 기사 ID {} 삭제 요청", organizationId, driverId);

        // 드라이버가 존재하는지 확인
        Driver driver = getDriverByIdAndOrganizationId(driverId, organizationId);

        // 드라이버 정보를 User로 전환하여 GUEST로 변경
        // Auth 컬렉션에서 해당 문서를 User로 조회
        User user = userRepository.findById(driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 기사 권한을 GUEST로 변경하고 조직 ID 제거
        user.updateRole(Role.GUEST);
        user.setOrganizationId("");

        userRepository.save(user);

        log.info("기사 {} 삭제 완료 (권한을 GUEST로 변경)", driverId);
        return true;
    }

    /**
     * 운전면허 번호 중복 확인
     */
    public boolean isLicenseNumberDuplicate(String licenseNumber, String organizationId) {
        return driverRepository.existsByOrganizationIdAndLicenseNumber(organizationId, licenseNumber);
    }

    /**
     * 첫 번째 요청 본문 생성
     */
    private Map<String, Object> createFirstRequestBody(LicenseVerifyRequestDto dto) {
        Map<String, Object> body = new HashMap<>();
        body.put("organization", dto.getOrganization());
        body.put("loginType", dto.getLoginType());
        body.put("loginUserName", dto.getLoginUserName());
        body.put("identity", dto.getIdentity());
        body.put("loginTypeLevel", dto.getLoginTypeLevel());
        body.put("phoneNo", dto.getPhoneNo());
        body.put("telecom", dto.getTelecom());
        body.put("birthDate", dto.getBirthDate());
        body.put("licenseNo01", dto.getLicenseNo01());
        body.put("licenseNo02", dto.getLicenseNo02());
        body.put("licenseNo03", dto.getLicenseNo03());
        body.put("licenseNo04", dto.getLicenseNo04());
        body.put("serialNo", dto.getSerialNo());
        body.put("userName", dto.getUserName());

        log.info("첫 번째 요청 본문: {}", toJsonString(body));
        return body;
    }

    /**
     * 두 번째 요청 본문 생성
     */
    private Map<String, Object> createSecondRequestBody(LicenseVerifyRequestDto dto,
                                                        Map<String, Object> firstRequestBody,
                                                        Map<String, Object> firstResponse) {
        // 첫 번째 응답에서 데이터 추출
        Map<String, Object> firstData = (Map<String, Object>) firstResponse.get("data");

        Map<String, Object> body = new HashMap<>(firstRequestBody);

        // 2단계 요청에 필요한 파라미터 추가
        body.put("simpleAuth", "1"); // 간편인증 승인
        body.put("is2Way", true);    // 2단계 요청 표시

        // 첫 번째 응답의 필수 파라미터 추가
        if (firstData.containsKey("jti")) {
            body.put("jti", firstData.get("jti"));
        }
        if (firstData.containsKey("twoWayTimestamp")) {
            body.put("twoWayTimestamp", firstData.get("twoWayTimestamp"));
        }

        // twoWayInfo 객체 설정
        Map<String, Object> twoWayInfo = new HashMap<>();
        twoWayInfo.put("jobIndex", 0);
        twoWayInfo.put("threadIndex", 0);
        if (firstData.containsKey("jti")) {
            twoWayInfo.put("jti", firstData.get("jti"));
        }
        if (firstData.containsKey("twoWayTimestamp")) {
            twoWayInfo.put("twoWayTimestamp", firstData.get("twoWayTimestamp"));
        }
        body.put("twoWayInfo", twoWayInfo);

        log.info("두 번째 요청 본문: {}", toJsonString(body));
        return body;
    }

    /**
     * 운전면허 진위확인 API 호출
     */
    private Map<String, Object> callDriverLicenseAPI(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("운전면허 진위확인 API 요청: {}", API_ENDPOINT);
            ResponseEntity<String> response = restTemplate.exchange(
                    API_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            log.info("운전면허 진위확인 API 응답 상태 코드: {}", response.getStatusCode());

            String responseBody = response.getBody();

            // 응답이 URL 인코딩되어 있는지 확인하고 디코딩
            if (responseBody != null && responseBody.startsWith("%")) {
                log.info("URL 인코딩된 응답 발견. 디코딩 시도...");
                responseBody = URLDecoder.decode(responseBody, StandardCharsets.UTF_8.name());
            }

            // 응답을 Map으로 변환
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = objectMapper.readValue(responseBody, Map.class);
                    return resultMap;
                } catch (Exception e) {
                    log.error("응답 JSON 파싱 중 오류: {}", e.getMessage(), e);
                    throw new BusinessException("응답 데이터 파싱 중 오류가 발생했습니다: " + e.getMessage());
                }
            } else {
                log.error("API 호출이 실패했습니다. 상태 코드: {}, 응답: {}", response.getStatusCode(), responseBody);
                throw new BusinessException("API 호출이 실패했습니다: " + response.getStatusCode());
            }
        } catch (RestClientResponseException e) {
            log.error("운전면허 진위확인 API 호출 중 오류 발생. 상태 코드: {}, 응답 본문: {}",
                    e.getRawStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("운전면허 진위확인 API 호출 중 오류: " + e.getMessage());
        } catch (Exception e) {
            log.error("운전면허 진위확인 API 호출 중 예외 발생", e);
            throw new BusinessException("운전면허 진위확인 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 첫 번째 응답 처리
     */
    private Map<String, String> processFirstResponse(Map<String, Object> response) {
        Map<String, String> result = new HashMap<>();
        result.put("resAuthenticity", "0");

        if (response != null && response.containsKey("result")) {
            Map<String, Object> resultInfo = (Map<String, Object>) response.get("result");
            String message = resultInfo.containsKey("message") ? (String) resultInfo.get("message") : "알 수 없는 오류";
            String extraMessage = resultInfo.containsKey("extraMessage") ? (String) resultInfo.get("extraMessage") : "";

            result.put("resAuthenticityDesc1", !extraMessage.isEmpty() ? extraMessage : message);
        } else {
            result.put("resAuthenticityDesc1", "유효하지 않은 응답");
        }

        return result;
    }

    /**
     * 두 번째 응답 처리
     */
    private Map<String, String> processSecondResponse(Map<String, Object> response) {
        Map<String, String> result = new HashMap<>();

        if (response == null) {
            result.put("resAuthenticity", "0");
            result.put("resAuthenticityDesc1", "응답 없음");
            return result;
        }

        // 결과 코드 확인
        if (response.containsKey("result")) {
            Map<String, Object> resultInfo = (Map<String, Object>) response.get("result");
            String code = resultInfo.containsKey("code") ? (String) resultInfo.get("code") : "";
            String message = resultInfo.containsKey("message") ? (String) resultInfo.get("message") : "알 수 없는 오류";
            String extraMessage = resultInfo.containsKey("extraMessage") ? (String) resultInfo.get("extraMessage") : "";

            // 성공 응답
            if ("CF-00000".equals(code)) {
                result.put("resAuthenticity", "1");
                result.put("resAuthenticityDesc1", "인증 성공");
            }
            // 오류 응답
            else {
                result.put("resAuthenticity", "0");
                result.put("resAuthenticityDesc1", !extraMessage.isEmpty() ? extraMessage : message);
            }
        }
        // 데이터가 있는 경우
        else if (response.containsKey("data") && !((Map<?, ?>) response.get("data")).isEmpty()) {
            Map<String, Object> data = (Map<String, Object>) response.get("data");

            // API 가이드에 따라 결과 매핑
            if (data.containsKey("resAuthenticity")) {
                result.put("resAuthenticity", String.valueOf(data.get("resAuthenticity")));
            } else {
                result.put("resAuthenticity", "0");
            }

            // 결과 설명 매핑
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (entry.getValue() instanceof String) {
                    result.put(entry.getKey(), (String) entry.getValue());
                } else if (entry.getValue() != null) {
                    result.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        else {
            result.put("resAuthenticity", "0");
            result.put("resAuthenticityDesc1", "유효하지 않은 응답 형식");
        }

        return result;
    }

    /**
     * 액세스 토큰 발급
     */
    private String getAccessToken() {
        log.info("액세스 토큰 요청 시작");
        HttpHeaders headers = new HttpHeaders();

        // Basic Auth 사용
        String auth = CLIENT_ID + ":" + CLIENT_SECRET;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes());
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

        // 요청 파라미터 설정
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.info("액세스 토큰 API 요청: {}", TOKEN_URL);
            ResponseEntity<String> response = restTemplate.exchange(
                    TOKEN_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            log.info("액세스 토큰 API 응답 상태 코드: {}", response.getStatusCode());

            // 응답 본문 로깅 (토큰 마스킹)
            String responseBody = response.getBody();
            log.info("액세스 토큰 API 응답 본문: {}", maskTokenInJson(responseBody));

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = objectMapper.readValue(responseBody, Map.class);
            return (String) tokenResponse.get("access_token");
        } catch (RestClientResponseException e) {
            log.error("액세스 토큰 API 호출 중 오류 발생. 상태 코드: {}, 응답 본문: {}",
                    e.getRawStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("액세스 토큰 획득 중 오류가 발생했습니다: " + e.getMessage());
        } catch (Exception e) {
            log.error("액세스 토큰 API 호출 중 예외 발생", e);
            throw new BusinessException("액세스 토큰 획득 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 2단계 인증이 필요한지 확인
     */
    private boolean isTwoWayAuthRequired(Map<String, Object> response) {
        if (response == null || !response.containsKey("data")) {
            return false;
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return data.containsKey("continue2Way") && (boolean) data.get("continue2Way");
    }

    /**
     * 면허번호 포맷팅
     */
    private String formattedLicenseNumber(LicenseVerifyRequestDto licenseData) {
        return licenseData.getLicenseNo01() +
                licenseData.getLicenseNo02() +
                licenseData.getLicenseNo03() +
                licenseData.getLicenseNo04();
    }

    /**
     * 기본 조직 정보 제공
     */
    public String getDefaultOrganization() {
        return ORGANIZATION;
    }

    /**
     * 기본 로그인 타입 제공
     */
    public String getDefaultLoginType() {
        return LOGIN_TYPE;
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 객체를 JSON 문자열로 변환
     */
    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString() + " (JSON 변환 실패)";
        }
    }

    /**
     * 토큰 마스킹
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "[MASKED]";
        }
        return token.substring(0, 5) + "..." + token.substring(token.length() - 5);
    }

    /**
     * JSON 내 토큰 값 마스킹
     */
    private String maskTokenInJson(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            if (map.containsKey("access_token")) {
                map.put("access_token", maskToken((String)map.get("access_token")));
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            // JSON 파싱 실패시 원본 반환 (로깅 용도로만 사용)
            if (json != null && json.contains("access_token")) {
                return json.replaceAll("\"access_token\"\\s*:\\s*\"[^\"]+\"", "\"access_token\":\"[MASKED]\"");
            }
            return json;
        }
    }
}