package capston2024.bustracker.config.dto.bus;

import capston2024.bustracker.domain.Bus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusStatusDTO {
    private String busNumber;
    private String busRealNumber;
    private String routeName;
    private Bus.OperationalStatus operationalStatus;
    private Bus.ServiceStatus serviceStatus;
    private int totalSeats;

    // 현재 운행 정보 (BusOperation에서 가져옴)
    private Integer currentPassengers;
    private Integer availableSeats;
    private String currentOperationId;
    private String currentDriverName;
    private boolean isCurrentlyOperating;
}