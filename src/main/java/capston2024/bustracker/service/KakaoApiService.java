package capston2024.bustracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class KakaoApiService {

    @Value("${kakao-rest-api-key}")
    private String REST_API_KEY;

    public String getDirections(String origin, String destination) {
        String url = "https://apis-navi.kakaomobility.com/v1/directions"
                + "?origin=" + origin
                + "&destination=" + destination
                + "&waypoints="
                + "&priority=RECOMMEND"
                + "&car_fuel=GASOLINE"
                + "&car_hipass=false"
                + "&alternatives=false"
                + "&road_details=false";

        // 헤더에 API Key 추가
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "KakaoAK " + REST_API_KEY);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class);
        return response.getBody();// 응답 내용 반환
    }
}
