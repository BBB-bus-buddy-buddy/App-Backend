package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusRegisterDTO {
    private String busNumber;         // 버스 번호
    private String routeId;           // 라우트 ID (이전 stationNames 대신)
    private int totalSeats;           // 총 좌석 수
}