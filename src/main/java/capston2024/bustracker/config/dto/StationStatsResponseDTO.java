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
public class StationStatsResponseDTO {
    private String stationId;
    private String stationName;
    private Double latitude;
    private Double longitude;
    private int lookbackDays;
    private long analysisStartTimestamp;
    private long analysisEndTimestamp;
    private long totalBoardings;
    private long totalAlightings;
    private long netPassengers;
    private double utilizationRate;
    private StationPeakInfoDTO busiestHour;
    private StationPeakInfoDTO busiestDay;
    private List<StationPeakInfoDTO> topHours;
    private String recommendation;
}
