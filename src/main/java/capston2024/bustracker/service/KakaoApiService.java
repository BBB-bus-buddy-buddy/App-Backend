package capston2024.bustracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

// API 문서: https://developers.kakaomobility.com/docs/navi-api/directions/
@Service
@Slf4j
public class KakaoApiService {

    @Value("${kakao-rest-api-key}")
    private String REST_API_KEY;

    /**
     * 단일 경유지 API
     * @param origin
     * @param destination
     * @return
     */
    public Integer getArrivalTime(String origin, String destination) {
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
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class);
        // 응답 본문에서 도착 시간을 추출
        Map<String, Object> body = response.getBody();
        if (body != null) {
            List<Map<String, Object>> routes = (List<Map<String, Object>>) body.get("routes");
            if (!routes.isEmpty()) {
                Map<String, Object> summary = (Map<String, Object>) routes.get(0).get("summary");
                return (Integer) summary.get("duration"); // 도착 예정 시간 (초 단위)
            }
        }
        return null; // 도착 시간이 없을 때 null 반환
    }

    public Integer getMultiwayArrivalTime(){
        return 0;
    }
}
