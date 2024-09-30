package capston2024.bustracker.controller;

import capston2024.bustracker.domain.Station;
import capston2024.bustracker.service.StationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/station")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    // 정류장 이름 or ID로 조회
    @GetMapping("/find")
    public ResponseEntity<List<Station>> getStation(@RequestParam String name) {
        log.info("가져 올 버스정류장의 이름: {}", name);
        List<Station> stations = stationService.getStation(name);
        if (stations.isEmpty()) {
            log.warn("해당 정류장을 찾을 수 없습니다: {}", name);
        }
        return ResponseEntity.ok(stations);
    }

    // 모든 정류장 조회
    @GetMapping("/all")
    public ResponseEntity<List<Station>> getAllStation() {
        log.info("모든 정류장 조회 요청");
        List<Station> stations = stationService.getAllStations();
        if (stations.isEmpty()) {
            log.warn("정류장을 찾을 수 없습니다");
        }
        return ResponseEntity.ok(stations);
    }

    // 정류장 ID로 조회
    @GetMapping("/{id}")
    public ResponseEntity<Station> getStationById(@PathVariable String id) {
        log.info("ID {}로 정류장 조회 중...", id);
        Station station = stationService.getStationById(id);
        return ResponseEntity.ok(station);
    }

    // 새로운 정류장 추가
    @PostMapping("/create")
    public ResponseEntity<Station> createStation(@RequestBody Station station) {
        log.info("새로운 정류장 추가 요청: {}", station.getName());
        Station createdStation = stationService.createStation(station);
        return ResponseEntity.ok(createdStation);
    }

    // 정류장 정보 수정
    @PutMapping("/update/{id}")
    public ResponseEntity<Station> updateStation(@PathVariable String id, @RequestBody Station station) {
        log.info("ID {}로 정류장 업데이트 요청", id);
        Station updatedStation = stationService.updateStation(id, station);
        return ResponseEntity.ok(updatedStation);
    }

    // 정류장 삭제
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteStation(@PathVariable String id) {
        log.info("ID {}로 정류장 삭제 요청", id);
        stationService.deleteStation(id);
        return ResponseEntity.noContent().build();
    }
}
