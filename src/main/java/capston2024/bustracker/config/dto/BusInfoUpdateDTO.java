package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusInfoUpdateDTO {
    private String busNumber;       // 버스 번호 (3~6자리 고유 코드)
    private String routeId;         // 라우트 ID
    private int totalSeats;         // 전체 좌석 수
}