package capston2024.bustracker.config.dto.busEtc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusStationDTO {
    private String id;
    private String name;
    private double latitude;
    private double longitude;
    private int sequence;
    private boolean isPassed;
    private boolean isCurrentStation;
    private String estimatedArrivalTime;
}