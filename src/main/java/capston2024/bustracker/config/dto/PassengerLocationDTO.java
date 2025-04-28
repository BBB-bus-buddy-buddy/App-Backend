package capston2024.bustracker.config.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 승객 앱에서 전송하는 위치 업데이트용 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassengerLocationDTO {
    private String userId;              // 사용자 ID
    private String organizationId;      // 조직 ID
    private double latitude;            // 위도
    private double longitude;           // 경도
    private long timestamp;             // 타임스탬프 (밀리초)
}