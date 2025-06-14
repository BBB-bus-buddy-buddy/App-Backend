package capston2024.bustracker.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 운행 중 위치 업데이트 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriveLocationUpdateDTO {

    @NotBlank(message = "운행 일정 ID는 필수입니다.")
    private String operationId;          // 운행 일정 ID

    @NotBlank(message = "버스 번호는 필수입니다.")
    private String busNumber;            // 버스 번호

    @NotNull(message = "위치 정보는 필수입니다.")
    private LocationInfo location;       // 현재 위치

    private Double speed;                // 속도 (m/s) - 선택
    private Double heading;              // 방향 (도) - 선택
    private Double accuracy;             // 정확도 (미터) - 선택

    /**
     * 위치 정보 내부 클래스
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        @NotNull(message = "위도는 필수입니다.")
        private Double latitude;

        @NotNull(message = "경도는 필수입니다.")
        private Double longitude;

        @NotNull(message = "타임스탬프는 필수입니다.")
        private Long timestamp;
    }
}