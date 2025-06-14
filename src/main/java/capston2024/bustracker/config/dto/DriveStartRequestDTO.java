package capston2024.bustracker.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 운행 시작 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriveStartRequestDTO {

    @NotBlank(message = "운행 일정 ID는 필수입니다.")
    private String operationId;          // 운행 일정 ID

    @NotNull(message = "조기 출발 여부는 필수입니다.")
    private boolean isEarlyStart;        // 조기 출발 여부

    @NotNull(message = "현재 위치 정보는 필수입니다.")
    private LocationInfo currentLocation; // 현재 위치 (출발지 확인용)

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