package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.StationRequestDTO;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.StationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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
    public ResponseEntity<ApiResponse<List<Station>>> getStations(@RequestParam(required = false) String name, @AuthenticationPrincipal OAuth2User principal) {
        log.info("유저의 principal : {} ", principal);
        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }
        String organizationId = (String) principal.getAttributes().get("organizationId");
        if (name != null && !name.isEmpty()) {
            // 정류장 이름으로 조회
            log.info("정류장 이름으로 조회: {}", name);
            List<Station> stations = stationService.getStationName(name, organizationId);

            //정류장을 찾을 수 없을 때
            if (stations.isEmpty()) {
                log.warn("해당 정류장을 찾을 수 없습니다: {}", name);
                return ResponseEntity.ok(new ApiResponse<>(stations, "해당 정류장이 없습니다."));
            }
            //이름 찾으면 나오는 로직
            log.info("검색된 버스정류장 이름: {}",
                    stations.stream()
                            .map(Station::getName)
                            .collect(Collectors.joining(", ")));
            return ResponseEntity.ok(new ApiResponse<> (stations, "버스 정류장 검색이 성공적으로 완료되었습니다."));
        } else {
            // 모든 정류장 조회
            log.info("모든 정류장 조회 요청");
            List<Station> stations = stationService.getAllStations(organizationId);
            // 찾는 정류장이 빈 값일 때
            if (stations.isEmpty()) {
                log.warn("정류장이 없습니다.");
                return ResponseEntity.ok(new ApiResponse<>(stations, "정류장이 없습니다."));
            }
            return ResponseEntity.ok(new ApiResponse<>(stations, "모든 정류장 조회 완료"));
        }
    }


    // 정류장 등록
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Station>> createStation(@AuthenticationPrincipal OAuth2User principal, @RequestBody StationRequestDTO createStationDTO) {
        log.info("새로운 정류장 등록 요청: {}", createStationDTO.getName());
        String organizationId = (String) principal.getAttributes().get("organizationId");
        try {
            Station createdStation = stationService.createStation(organizationId, createStationDTO);
            return ResponseEntity.ok(new ApiResponse<>(createdStation, "정류장이 성공적으로 등록되었습니다."));
        } catch (BusinessException e) {
            log.error("정류장 등록 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(null, "중복된 정류장이 이미 존재합니다."));
        }
    }

    // 정류장 업데이트
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<String>> updateStation(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String id,
            @RequestBody StationRequestDTO stationRequestDTO) {

        log.info("{} 정류장 업데이트 요청 (ID: {})", stationRequestDTO.getName(), id);
        String organizationId = (String) principal.getAttributes().get("organizationId");

        // 업데이트 작업 수행
        boolean result = stationService.updateStation(organizationId, id, stationRequestDTO);

        if (result) {
            // 성공적인 업데이트 응답
            return ResponseEntity.ok(new ApiResponse<>(null, "정류장이 성공적으로 업데이트되었습니다."));
        } else {
            // 업데이트 실패 응답
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(null, "정류장 업데이트에 실패했습니다."));
        }
    }



    // 정류장 삭제
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteStation(@PathVariable String id) {
        log.info("정류장 ID {}로 삭제 요청", id);
        stationService.deleteStation(id);
        return ResponseEntity.ok(new ApiResponse<>(null, "정류장이 성공적으로 삭제되었습니다."));
    }
}
