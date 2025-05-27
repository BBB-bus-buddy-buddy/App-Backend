package capston2024.bustracker.config.dto.busEtc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 기사 앱에서 서버로 전송하는 실시간 위치 업데이트 DTO
 * 버스 기사의 위치 정보와 운행 상태를 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationUpdateDTO {
    private String operationId;        // 현재 운행 ID (필수)
    private String busNumber;          // 버스 번호 (선택, fallback용)
    private String organizationId;     // 조직 ID
    private double latitude;           // 위도
    private double longitude;          // 경도
    private int currentPassengers;     // 현재 승객 수
    private long timestamp;            // 타임스탬프 (밀리초)
    private String currentStationId;   // 현재 정류장 ID (선택)
    private Double speed;              // 현재 속도 (km/h, 선택)
    private Double heading;            // 진행 방향 (0-360도, 선택)

    /**
     * 위치가 유효한지 확인
     */
    public boolean isValidLocation() {
        return latitude >= -90 && latitude <= 90 &&
                longitude >= -180 && longitude <= 180;
    }

    /**
     * 필수 필드가 모두 있는지 확인
     */
    public boolean hasRequiredFields() {
        return operationId != null && !operationId.isEmpty() &&
                isValidLocation() &&
                timestamp > 0;
    }
}