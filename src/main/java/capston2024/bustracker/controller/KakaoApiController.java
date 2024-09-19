package capston2024.bustracker.controller;

import capston2024.bustracker.service.KakaoApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KakaoApiController {

    private final KakaoApiService kakaoApiService;

    @GetMapping("/directions")
    public ResponseEntity<String> getDirections(
            @RequestParam String origin,
            @RequestParam String destination) {
        String response = kakaoApiService.getDirections(origin, destination);
        return ResponseEntity.ok(response);
    }
}
