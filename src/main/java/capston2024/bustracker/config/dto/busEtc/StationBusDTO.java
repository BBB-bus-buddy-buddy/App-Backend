package capston2024.bustracker.config.dto.busEtc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationBusDTO {
    private String busNumber;
    private String busRealNumber;
    private String routeName;
    private String organizationId;
    private double latitude;
    private double longitude;
    private int totalSeats;
    private int currentPassengers;
    private int availableSeats;
    private String currentStationName;
    private String operationId;
    private boolean isOperating;
    private long lastUpdateTime;
    private String estimatedArrivalTime; // 이 정류장까지의 예상 도착 시간
}