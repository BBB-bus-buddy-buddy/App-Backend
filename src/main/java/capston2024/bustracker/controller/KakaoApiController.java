package capston2024.bustracker.controller;

import capston2024.bustracker.service.KakaoApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class KakaoApiController {

    private final KakaoApiService kakaoApiService;

    @GetMapping("/arrival-time")
    public ResponseEntity<String> getArrivalTime(
            @RequestParam String origin,
            @RequestParam String destination) {
        log.info("도착 예정 시간을 요청 받았습니다. 출발지: {}, 목적지: {}", origin, destination);
        Integer arrivalTimeInSeconds = kakaoApiService.getArrivalTime(origin, destination);

        if (arrivalTimeInSeconds == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("도착 예정 시간을 불러오지 못했습니다.");
        }

        int minutes = arrivalTimeInSeconds / 60;
        int seconds = arrivalTimeInSeconds % 60;

        String responseMessage = String.format("도착 예정 시간: %d분 %d초", minutes, seconds);
        return ResponseEntity.ok(responseMessage);
    }
}
