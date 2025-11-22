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
public class NetworkAnalysisRequestDTO {
    /**
     * 분석 시작 날짜 (YYYY-MM-DD 형식)
     * null이면 endDate로부터 lookbackDays만큼 이전으로 설정
     */
    private String startDate;

    /**
     * 분석 종료 날짜 (YYYY-MM-DD 형식)
     * null이면 현재 날짜로 설정
     */
    private String endDate;

    /**
     * 분석 기간(일) - startDate가 없을 때 사용
     * 기본값: 7일
     */
    private Integer lookbackDays;

    /**
     * 조직 ID 필터 (null이면 전체)
     */
    private String organizationId;

    /**
     * 노선 ID 필터 (null이면 전체)
     */
    private List<String> routeIds;

    /**
     * 정류장 ID 필터 (null이면 전체)
     */
    private List<String> stationIds;

    /**
     * 집계 타입 (HOUR, DAY, WEEK, MONTH, DAY_OF_WEEK)
     * null이면 모든 타입 반환
     */
    private String aggregationType;

    /**
     * 노선별 통계 포함 여부
     * 기본값: true
     */
    private Boolean includeRouteStats;

    /**
     * 시간대별 통계 포함 여부
     * 기본값: true
     */
    private Boolean includeTimeStats;
}
