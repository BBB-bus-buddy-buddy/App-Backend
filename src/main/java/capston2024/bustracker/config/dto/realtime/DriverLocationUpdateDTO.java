package capston2024.bustracker.config.dto.realtime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 기사 앱 → 서버 (위치 전송)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationUpdateDTO {
    private String operationId;        // 현재 운행 ID
    private String busNumber;          // 버스 번호 (fallback)
    private String organizationId;     // 조직 ID
    private double latitude;           // 위도
    private double longitude;          // 경도
    private int currentPassengers;     // 현재 승객 수
    private long timestamp;            // 타임스탬프
    private String currentStationId;   // 현재 정류장 ID (선택)
}
