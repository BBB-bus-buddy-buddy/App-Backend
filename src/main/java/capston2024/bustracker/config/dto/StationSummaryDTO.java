package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StationSummaryDTO {
    private String stationId;
    private String stationName;
    private long totalBoardings;
    private long totalAlightings;
    private double utilizationRate;
    private String peakHourLabel;
    private String recommendation;
}
