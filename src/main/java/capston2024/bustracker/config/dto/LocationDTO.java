package capston2024.bustracker.config.dto;

import lombok.*;

import java.time.Instant;

@Getter @Setter
@RequiredArgsConstructor
public class LocationDTO {
    private double latitude;  // 위도
    private double longitude; // 경도
    private Instant timestamp; // 위치를 수신한 시간
}
