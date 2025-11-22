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
public class RouteSummaryDTO {
    /**
     * 노선 ID
     */
    private String routeId;

    /**
     * 노선 이름
     */
    private String routeName;

    /**
     * 총 탑승 횟수
     */
    private long totalBoardings;

    /**
     * 총 하차 횟수
     */
    private long totalAlightings;

    /**
     * 이용률 (탑승 수 / 좌석 수)
     */
    private double utilizationRate;

    /**
     * 피크 시간대
     */
    private String peakHourLabel;

    /**
     * 가장 많이 이용된 정류장 상위 5개
     */
    private List<StationSummaryDTO> topStations;

    /**
     * 권장 사항
     */
    private String recommendation;
}
