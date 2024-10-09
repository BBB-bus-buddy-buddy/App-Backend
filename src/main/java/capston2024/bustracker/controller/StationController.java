package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.service.StationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("/api/station")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    // 정류장 이름으로 조회 또는 모든 정류장 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<Station>>> getStations(@RequestParam(required = false) String name) {
        if (name != null && !name.isEmpty()) {
            // 정류장 이름으로 조회
            log.info("정류장 이름으로 조회: {}", name);
            List<Station> stations = stationService.getStationName(name);
            if (stations.isEmpty()) {
                log.warn("해당 정류장을 찾을 수 없습니다: {}", name);
                return ResponseEntity.ok(new ApiResponse<>(stations, "해당 정류장이 없습니다."));
            }
            log.info("검색된 버스정류장 이름: {}",
                    stations.stream()
                            .map(Station::getName)
                            .collect(Collectors.joining(", ")));
            return ResponseEntity.ok(new ApiResponse<>(stations, "버스 정류장 검색이 성공적으로 완료되었습니다."));
        } else {
            // 모든 정류장 조회
            log.info("모든 정류장 조회 요청");
            List<Station> stations = stationService.getAllStations();
            if (stations.isEmpty()) {
                log.warn("정류장이 없습니다.");
                return ResponseEntity.ok(new ApiResponse<>(stations, "정류장이 없습니다."));
            }
            return ResponseEntity.ok(new ApiResponse<>(stations, "모든 정류장 조회 완료"));
        }
    }


    // 정류장 등록
    @PostMapping
    public ResponseEntity<ApiResponse<Station>> createStation(@RequestBody Station station) {
        log.info("새로운 정류장 등록 요청: {}", station.getName());
        Station createdStation = stationService.createStation(station);
        return ResponseEntity.ok(new ApiResponse<>(createdStation, "정류장이 성공적으로 등록되었습니다."));
    }

    // 정류장 업데이트
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Station>> updateStation(@PathVariable String id, @RequestBody Station station) {
        log.info("정류장 ID {}로 업데이트 요청", id);
        Station updatedStation = stationService.updateStation(id, station);
        return ResponseEntity.ok(new ApiResponse<>(updatedStation, "정류장이 성공적으로 업데이트되었습니다."));
    }

    // 정류장 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStation(@PathVariable String id) {
        log.info("정류장 ID {}로 삭제 요청", id);
        stationService.deleteStation(id);
        return ResponseEntity.ok(new ApiResponse<>(null, "정류장이 성공적으로 삭제되었습니다."));
    }
}
