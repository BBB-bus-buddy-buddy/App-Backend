package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TimeBasedStatsDTO {
    /**
     * 라벨 (시간대: "14:00-15:00", 날짜: "2025-01-15", 요일: "MONDAY", 주차: "Week 2", 월: "January 2025")
     */
    private String label;

    /**
     * 타임스탬프 (정렬용)
     */
    private Long timestamp;

    /**
     * 총 탑승 횟수
     */
    private long totalBoardings;

    /**
     * 총 하차 횟수
     */
    private long totalAlightings;

    /**
     * 순 승객 수 (탑승 - 하차)
     */
    private long netPassengers;

    /**
     * 이용률 (탑승 수 / 좌석 수)
     */
    private double utilizationRate;

    /**
     * 평균 이용률 (여러 버스/정류장의 평균)
     */
    private Double averageUtilizationRate;
}
