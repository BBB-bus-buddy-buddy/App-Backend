package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class LocationDTO {
    private double latitude;  // 위도
    private double longitude; // 경도
    private int seat;
    private Instant timestamp; // 위치를 수신한 시간
}
