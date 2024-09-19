package capston2024.bustracker.controller;

import capston2024.bustracker.domain.Station;
import capston2024.bustracker.service.StationFindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
public class StationController {

    private final StationFindService stationFindService;
    public StationController(StationFindService stationFindService) {
        this.stationFindService = stationFindService;
    }

    // 정류장 이름 or ID로 조회
    @GetMapping("/station")
    public ResponseEntity<List<Station>> getStation(@RequestParam String name) {
        log.info("가져 올 버스정류장의 아이디: {}", name);
        List<Station> stations = stationFindService.getStationByName(name);
        if (stations.isEmpty()) {
            log.warn("해당 정류장을 찾을 수 없습니다: {}", name);
        }
        log.info("조회된 정류장 수: {}", stations.size());  // 가져온 정류장 개수를 출력
        return ResponseEntity.ok(stations);
    }

    // 모든 정류장 조회
    @GetMapping("/station/all")
    public ResponseEntity<List<Station>> getAllStation() {
        log.info("클라이언트 요청: 모든 정류장 조회");
        List<Station> stations = stationFindService.getAllStations();
        if (stations.isEmpty()) {
            log.warn("정류장을 찾을 수 없습니다");
        }
        log.info("조회된 정류장 수: {}", stations.size());  // 가져온 정류장 개수를 출력
        return ResponseEntity.ok(stations);
    }
}