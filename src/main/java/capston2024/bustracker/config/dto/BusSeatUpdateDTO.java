package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.time.Instant;

/**
 * 버스 위치, 좌석 정보 처리 DTO
 */
@RequiredArgsConstructor
@Getter
@Setter
public class BusSeatUpdateDTO {
    private final String busNumber;
    private final int seats;
    private final Instant timestamp;
}