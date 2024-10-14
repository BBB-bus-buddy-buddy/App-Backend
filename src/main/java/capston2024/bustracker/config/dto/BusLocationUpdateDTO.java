package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.time.Instant;

/**
 * 버스 위치 정보 처리 DTO
 */
@AllArgsConstructor
@RequiredArgsConstructor
@Getter
@Setter
public class BusLocationUpdateDTO {
    private final String busNumber;
    private final GeoJsonPoint location;
    private final Instant timestamp;
}