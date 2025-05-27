package capston2024.bustracker.config.dto.busEtc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatInfoDTO {
    private String operationId;       // 운행 ID
    private String busNumber;         // 버스 번호
    private int totalSeats;           // 총 좌석 수
    private int currentPassengers;    // 현재 승객 수
    private int availableSeats;       // 사용 가능 좌석 수
    private long lastUpdated;         // 마지막 업데이트 시간
}