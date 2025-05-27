package capston2024.bustracker.config.dto.bus;

import capston2024.bustracker.domain.Bus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusCreateDTO {
    private String routeId;
    private int totalSeats;
    private String busRealNumber;
    private Bus.OperationalStatus operationalStatus = Bus.OperationalStatus.ACTIVE;
    private Bus.ServiceStatus serviceStatus = Bus.ServiceStatus.NOT_IN_SERVICE;
}