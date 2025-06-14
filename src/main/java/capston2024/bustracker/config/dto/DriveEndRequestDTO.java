package capston2024.bustracker.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 운행 종료 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriveEndRequestDTO {

    @NotBlank(message = "운행 일정 ID는 필수입니다.")
    private String operationId;          // 운행 일정 ID

    @NotNull(message = "현재 위치 정보는 필수입니다.")
    private LocationInfo currentLocation; // 현재 위치 (종착지 확인용)

    private String endReason;            // 운행 종료 사유 (선택)

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

        private Long timestamp;  // 위치 측정 시간 (선택)
    }
}