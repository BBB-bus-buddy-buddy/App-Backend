package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.BusArrivalEstimateResponseDTO;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.service.KakaoApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/kakao-api")
public class KakaoApiController {

    private final KakaoApiService kakaoApiService;

    @GetMapping("/arrival-time/multi")
    public ResponseEntity<ApiResponse<BusArrivalEstimateResponseDTO>> getTimeEstimate(
            @RequestParam String busId,
            @RequestParam String stationId) {
        BusArrivalEstimateResponseDTO result = kakaoApiService.getMultiWaysTimeEstimate(busId, stationId);
        log.info("다중 도착예정 처리 결과 : {}", result);
        return ResponseEntity.ok(new ApiResponse<>(result, "다중 도착예정시간이 성공적으로 조회되었습니다."));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("찾을 수 없는 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(false, ex.getMessage()));
    }

}
