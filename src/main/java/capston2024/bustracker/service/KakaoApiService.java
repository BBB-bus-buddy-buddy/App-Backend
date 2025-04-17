package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.Route;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.RouteRepository;
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
    private RouteRepository routeRepository;

    @Autowired
    private StationRepository stationRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 단일 경유지 API
     *
     * @param origin 출발지 정보
     * @param destination 목적지 정보
     * @return 예상 소요 시간 (포맷: "x시간 y분 z초")
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
                return formatDurationDetailed(duration);
            }
        }
        return null; // 도착 시간이 없을 때 null 반환
    }

    /**
     * 다중 경유지를 포함한 남은시간 계산 API
     * @param busId 버스 ID
     * @param stationId 목적지 정류장 ID
     * @return 예상 소요 시간 및 경유지 정보
     */
    public BusTimeEstimateResponse getMultiWaysTimeEstimate(String busId, String stationId) {
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 버스 입니다: " + busId));

        Station targetStation = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 정류장 입니다: " + stationId));

        // 버스의 라우트 정보 조회
        Route route = null;
        if (bus.getRouteId() != null) {
            route = routeRepository.findById(bus.getRouteId().getId().toString())
                    .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 라우트 입니다: " + bus.getRouteId().getId()));
        } else {
            throw new ResourceNotFoundException("버스에 라우트 정보가 없습니다: " + busId);
        }

        // 목표 정류장의 순서 찾기
        int targetIndex = findStationIndexInRoute(route, stationId);
        log.info("targetIdx: {}", targetIndex);
        log.info("prevStationIdx: {}", bus.getPrevStationIdx());

        // 이미 지난 정류장인지 확인
        if (targetIndex <= bus.getPrevStationIdx()) {
            return BusTimeEstimateResponse.builder()
                    .estimatedTime("--분 --초")
                    .waypoints(Collections.emptyList())
                    .build();
        }

        // 현재 정류장 다음부터 목표 정류장 전까지의 정류장들을 경유지로 추출
        List<Station> waypoints = extractWaypointsFromRoute(route, bus.getPrevStationIdx(), targetIndex);
        log.info("waypoints: {}", waypoints);

        // 카카오 모빌리티 API 호출
        KakaoDirectionsResponse response = requestRouteEstimate(bus, targetStation, waypoints);
        log.info("response: {}", response);

        return BusTimeEstimateResponse.builder()
                .estimatedTime(formatDuration(response.getDuration()))
                .waypoints(waypoints.stream().map(Station::getName).collect(Collectors.toList()))
                .build();
    }

    /**
     * 라우트에서 특정 정류장의 인덱스 찾기
     */
    private int findStationIndexInRoute(Route route, String stationId) {
        for (int i = 0; i < route.getStations().size(); i++) {
            Route.RouteStation routeStation = route.getStations().get(i);
            if (routeStation.getStationId().getId().toString().equals(stationId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("해당 정류장은 라우트의 경유 정류장에 포함되지 않습니다: " + stationId);
    }

    /**
     * 라우트에서 특정 구간의 정류장들을 추출
     */
    private List<Station> extractWaypointsFromRoute(Route route, int startIndex, int endIndex) {
        List<Station> waypoints = new ArrayList<>();

        // 시작 인덱스 다음부터 끝 인덱스 전까지의 정류장들만 추출
        for (int i = startIndex + 1; i < endIndex; i++) {
            Route.RouteStation routeStation = route.getStations().get(i);
            String stationRefId = routeStation.getStationId().getId().toString();
            Station station = stationRepository.findById(stationRefId)
                    .orElseThrow(() -> new ResourceNotFoundException("정류장을 찾을 수 없습니다: " + stationRefId));
            waypoints.add(station);
        }

        return waypoints;
    }

    /**
     * 경로 탐색 요청 생성 및 API 호출
     */
    private KakaoDirectionsResponse requestRouteEstimate(Bus bus, Station targetStation, List<Station> waypoints) {
        KakaoDirectionsRequest request = KakaoDirectionsRequest.builder()
                .origin(new KakaoPoint(
                        bus.getLocation().getY(), // longitude
                        bus.getLocation().getX())) // latitude
                .destination(new KakaoPoint(
                        targetStation.getLocation().getY(), // longitude
                        targetStation.getLocation().getX())) // latitude
                .waypoints(waypoints.stream()
                        .map(station -> new KakaoPoint(
                                station.getLocation().getY(), // longitude
                                station.getLocation().getX())) // latitude
                        .collect(Collectors.toList()))
                .priority("RECOMMEND")
                .build();

        return callKakaoMobilityAPI(request);
    }

    /**
     * 카카오 모빌리티 API 호출
     */
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

            log.info("KAKAO API response body: {}", response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("카카오 API 호출 에러", e);
            throw new RuntimeException("남은 시간 계산에 실패하였습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 초 단위 시간을 "분 초" 형식으로 변환
     */
    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d분 %d초", minutes, remainingSeconds);
    }

    /**
     * 초 단위 시간을 "시간 분 초" 형식으로 변환
     */
    private String formatDurationDetailed(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainingSeconds = seconds % 60;

        StringBuilder formattedTime = new StringBuilder();
        if (hours > 0) formattedTime.append(hours).append("시간 ");
        if (minutes > 0) formattedTime.append(minutes).append("분 ");
        if (remainingSeconds > 0 || formattedTime.length() == 0)
            formattedTime.append(remainingSeconds).append("초");

        return formattedTime.toString();
    }
}