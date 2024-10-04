package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class BusDTO {
    private String id; // MongoDB에서 자동 생성될 _id
    private String busNumber;
    private int totalSeats; // 전체 좌석
    private int occupiedSeats; // 앉은 좌석 수
    private int availableSeats; // 남은 좌석 수
    private GeoJsonPoint location; // 좌표 정보 (GeoJSON 형식)
    private List<String> stationsNames; // 버스의 노선(정류장 이름 목록)
    private Instant timestamp; // 위치 정보 최신화
}
