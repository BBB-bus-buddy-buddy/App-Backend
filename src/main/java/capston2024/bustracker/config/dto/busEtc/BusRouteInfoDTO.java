package capston2024.bustracker.config.dto.busEtc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusRouteInfoDTO {
    private String busNumber;
    private String busRealNumber;
    private String routeName;
    private String organizationId;

    // 현재 위치 정보
    private double latitude;
    private double longitude;

    // 현재 정류장 정보
    private BusStationDTO currentStation;

    // 다음 정류장 정보
    private BusStationDTO nextStation;

    // 다음 정류장까지의 예상 시간
    private String estimatedTimeToNext;
    private int remainingSecondsToNext;

    // 전체 노선 정보
    private List<BusStationDTO> allStations;

    // 좌석 정보
    private int totalSeats;
    private int currentPassengers;
    private int availableSeats;

    // 운행 정보
    private String operationId;
    private boolean isOperating;
    private long lastUpdateTime;
}
