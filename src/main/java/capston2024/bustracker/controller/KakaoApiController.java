package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.ArrivalTimeRequestDTO;
import capston2024.bustracker.config.dto.ArrivalTimeResponseDTO;
import capston2024.bustracker.config.dto.BusTimeEstimateResponse;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.service.BusService;
import capston2024.bustracker.service.KakaoApiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/kakao-api")
public class KakaoApiController {

    private final KakaoApiService kakaoApiService;
    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/arrival-time/single")
    public ResponseEntity<ApiResponse<ArrivalTimeResponseDTO>> getSingleArrivalTime(
            @RequestParam String origin,
            @RequestParam String destination) {
        log.info("도착 예정 시간을 요청 받았습니다. 출발지: {}, 목적지: {}", origin, destination);
        String[] origins = origin.split(",");
        ArrivalTimeRequestDTO originDTO = new ArrivalTimeRequestDTO(origins[0], Double.parseDouble(origins[1]), Double.parseDouble(origins[2]));
        String[] destinations = destination.split(",");
        ArrivalTimeRequestDTO destinationDTO = new ArrivalTimeRequestDTO(destinations[0], Double.parseDouble(destinations[1]), Double.parseDouble(destinations[2]));
        String arrivalTimeInSeconds = kakaoApiService.getSingleWaysTimeEstimate(originDTO, destinationDTO);
        return ResponseEntity.ok(new ApiResponse<>(new ArrivalTimeResponseDTO(originDTO.getName(), arrivalTimeInSeconds), "도착예정시간이 성공적으로 조회되었습니다."));
    }

    @GetMapping("/arrival-time/multi")
    public ResponseEntity<ApiResponse<BusTimeEstimateResponse>> getTimeEstimate(
            @RequestParam String busId,
            @RequestParam String stationId) {
        return ResponseEntity.ok(new ApiResponse<>(kakaoApiService.getMultiWaysTimeEstimate(busId, stationId), "다중 도착예정시간이 성공적으로 조회되었습니다."));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("찾을 수 없는 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(false, ex.getMessage()));
    }

}
