package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 버스 기사 앱에서 전송하는 위치 업데이트용 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusLocationUpdateDTO {
    private String busNumber;           // 버스 번호
    private String organizationId;      // 조직 ID
    private double latitude;            // 위도
    private double longitude;           // 경도
    private int occupiedSeats;          // 현재 사용 중인 좌석 수
    private long timestamp;             // 타임스탬프 (밀리초)
}