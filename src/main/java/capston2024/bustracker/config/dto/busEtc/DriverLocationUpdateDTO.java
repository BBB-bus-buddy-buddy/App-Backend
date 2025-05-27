package capston2024.bustracker.config.dto.busEtc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationUpdateDTO {
    private String operationId;        // 현재 운행 ID (핵심!)
    private double latitude;           // 위도
    private double longitude;          // 경도
    private int currentPassengers;     // 현재 승객 수
    private long timestamp;            // 타임스탬프
}