package capston2024.bustracker.config.dto.bus;

import capston2024.bustracker.domain.Bus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusUpdateDTO {
    private String busNumber;
    private String busRealNumber;
    private String routeId;
    private Integer totalSeats;
    private Bus.OperationalStatus operationalStatus;
    private Bus.ServiceStatus serviceStatus;
}
