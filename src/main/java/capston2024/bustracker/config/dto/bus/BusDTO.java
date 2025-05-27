package capston2024.bustracker.config.dto.bus;

import capston2024.bustracker.domain.Bus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusDTO {
    private String id;
    private String busNumber;
    private String busRealNumber;
    private String organizationId;
    private int totalSeats;
    private String routeId;
    private String routeName;
    private Bus.OperationalStatus operationalStatus;
    private Bus.ServiceStatus serviceStatus;
}