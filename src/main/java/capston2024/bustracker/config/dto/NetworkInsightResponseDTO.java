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
    private int lookbackDays;
    private long analysisStartTimestamp;
    private long analysisEndTimestamp;
    private List<StationSummaryDTO> busiestStations;
    private List<StationSummaryDTO> overcrowdedStations;
    private List<StationSummaryDTO> allStationSummaries;
    private List<String> recommendations;
}
