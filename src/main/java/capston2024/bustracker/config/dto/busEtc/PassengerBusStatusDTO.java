package capston2024.bustracker.config.dto.busEtc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerBusStatusDTO {
    private String operationId;           // 운행 ID
    private String busNumber;             // 버스 번호
    private String busRealNumber;         // 실제 버스 번호
    private String routeName;             // 노선명
    private String organizationId;        // 조직 ID
    private double latitude;              // 현재 위도
    private double longitude;             // 현재 경도
    private int totalSeats;               // 총 좌석 수
    private int currentPassengers;        // 현재 승객 수
    private int availableSeats;           // 사용 가능 좌석 수
    private String currentStationName;    // 현재 정류장명
    private String driverName;            // 기사명
    private long lastUpdateTime;          // 마지막 업데이트 시간
    private boolean isActive;             // 운행 활성 상태
}