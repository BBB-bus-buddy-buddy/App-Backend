package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.StationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.util.stream.Collectors;

// API 문서: https://developers.kakaomobility.com/docs/navi-api/directions/
@Service
@Slf4j
public class KakaoApiService {

    @Value("${KAKAO_REST_API_KEY}")
    private String REST_API_KEY;

    @Autowired
    private BusRepository busRepository;
    @Autowired
    private StationRepository stationRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 단일 경유지 API
     *
     * @param origin
     * @param destination
     * @return
     */
    public String getSingleWaysTimeEstimate(ArrivalTimeRequestDTO origin, ArrivalTimeRequestDTO destination) {
        String url = "https://apis-navi.kakaomobility.com/v1/directions"
                + "?origin=" + origin.getX() + "," + origin.getY()
                + "&destination=" + destination.getX() + "," + destination.getY()
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
                int duration = Integer.parseInt(summary.get("duration").toString()); // 도착 예정 시간 (초 단위)
                int hours = duration / 3600;
                int minutes = (duration % 3600) / 60;
                int seconds = duration % 60;
                StringBuilder formattedTime = new StringBuilder();
                if (hours > 0) formattedTime.append(hours).append("시간 ");
                if (minutes > 0) formattedTime.append(minutes).append("분 ");
                if (seconds > 0 || formattedTime.length() == 0) formattedTime.append(seconds).append("초"); // 0초일 때도 출력해야 하는 경우
                return formattedTime.toString();
            }
        }
        return null; // 도착 시간이 없을 때 null 반환
    }

    /**
     * 다중 경유지를 포함한 남은시간 계산 API
     * @param busId
     * @param stationId
     * @return
     */
    public BusTimeEstimateResponse getMultiWaysTimeEstimate(String busId, String stationId) {
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 버스 입니다: " + busId));

        Station targetStation = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 정류장 입니다: " + stationId));

        // 이미 지난 정류장인지 확인 (현재(최근) 인덱스보다 이전 인덱스의 정류장이면 이미 지난 것)
        int targetIndex = findStationIndex(bus, targetStation.getId());
        log.info("targetIdx : {}", targetIndex);
        log.info("prevStationIdx : {}", bus.getPrevStationIdx());
        if (targetIndex <= bus.getPrevStationIdx()) {
            return BusTimeEstimateResponse.builder()
                    .estimatedTime("--분 --초")
                    .waypoints(Collections.emptyList())
                    .build();
        }

        // 현재 정류장 다음부터 목표 정류장 전까지의 정류장들을 경유지로 추출
        List<Station> waypoints = extractWaypoints(bus, targetIndex);
        log.info("waypoints : {}", waypoints);

        // 카카오 모빌리티 API 호출
        KakaoDirectionsResponse response = requestRouteEstimate(bus, targetStation, waypoints);
        log.info("response : {}", response);

        return BusTimeEstimateResponse.builder()
                .estimatedTime(formatDuration(response.getDuration()))
                .waypoints(waypoints.stream().map(Station::getName).collect(Collectors.toList()))
                .build();
    }

    private int findStationIndex(Bus bus, String stationId) {
        for (int i = 0; i < bus.getStations().size(); i++) {
            if (bus.getStations().get(i).getStationRef().getId().equals(stationId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("해당 정류장은 버스의 경유 정류장에 포함되지 않습니다: " + stationId);
    }

    private List<Station> extractWaypoints(Bus bus, int targetIndex) {
        List<Station> waypoints = new ArrayList<>();

        // 현재 정류장 다음부터 목표 정류장 전까지의 정류장들만 추출
        for (int i = bus.getPrevStationIdx() + 1; i < targetIndex; i++) {
            Bus.StationInfo station = bus.getStations().get(i);
            Station stationInfo = stationRepository.findById((String) station.getStationRef().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "정류장을 찾을 수 없습니다: " + station.getStationRef().getId()));
            waypoints.add(stationInfo);
        }

        return waypoints;
    }
    private KakaoDirectionsResponse requestRouteEstimate(Bus bus, Station targetStation,
                                                         List<Station> waypoints) {
        KakaoDirectionsRequest request = KakaoDirectionsRequest.builder()
                .origin(new KakaoPoint(
                        bus.getLocation().getCoordinates().get(1),  // longitude
                        bus.getLocation().getCoordinates().get(0))) // latitude
                .destination(new KakaoPoint(
                        targetStation.getLocation().getCoordinates().get(1),  // longitude
                        targetStation.getLocation().getCoordinates().get(0))) // latitude
                .waypoints(waypoints.stream()
                        .map(station -> new KakaoPoint(
                                station.getLocation().getCoordinates().get(1),  // longitude
                                station.getLocation().getCoordinates().get(0))) // latitude
                        .collect(Collectors.toList()))
                .priority("RECOMMEND")
                .build();

        return callKakaoMobilityAPI(request);
    }

    private KakaoDirectionsResponse callKakaoMobilityAPI(KakaoDirectionsRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + REST_API_KEY);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<KakaoDirectionsRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<KakaoDirectionsResponse> response = restTemplate.exchange(
                    "https://apis-navi.kakaomobility.com/v1/waypoints/directions",
                    HttpMethod.POST,
                    entity,
                    KakaoDirectionsResponse.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new RuntimeException("카카오 API에서 정보를 얻지 못하였습니다");
            }
            log.info("KAKAO API response body : {}", response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("카카오 API 호출 에러", e);
            throw new RuntimeException("남은 시간 계산에 실패하였습니다 : ", e);
        }
    }
    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d분 %d초", minutes, remainingSeconds);
    }
}
