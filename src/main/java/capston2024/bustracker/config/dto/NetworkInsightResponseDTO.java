package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NetworkInsightResponseDTO {
    // 기본 분석 기간 정보
    private int lookbackDays;
    private long analysisStartTimestamp;
    private long analysisEndTimestamp;
    private String startDate;
    private String endDate;

    // 정류장별 통계
    private List<StationSummaryDTO> busiestStations;
    private List<StationSummaryDTO> overcrowdedStations;
    private List<StationSummaryDTO> allStationSummaries;

    // 노선별 통계
    private List<RouteSummaryDTO> routeSummaries;
    private List<RouteSummaryDTO> busiestRoutes;

    // 시간대별 통계
    private List<TimeBasedStatsDTO> hourlyStats;
    private List<TimeBasedStatsDTO> dailyStats;
    private List<TimeBasedStatsDTO> weeklyStats;
    private List<TimeBasedStatsDTO> monthlyStats;
    private List<TimeBasedStatsDTO> dayOfWeekStats;

    // 권장 사항
    private List<String> recommendations;

    // 필터 정보 (어떤 필터가 적용되었는지)
    private FilterInfoDTO appliedFilters;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FilterInfoDTO {
        private String organizationId;
        private List<String> routeIds;
        private List<String> stationIds;
        private String aggregationType;
    }
}
