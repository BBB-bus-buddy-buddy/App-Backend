package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

@Getter @Setter
@AllArgsConstructor
public class BusArrivalDTO {
    private String busId;
    private int seatCount;
    private GeoJsonPoint location;
    private int estimatedArrivalTime; // 도착 예정 시간 (분 단위)
}
