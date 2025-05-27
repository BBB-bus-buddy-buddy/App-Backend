package capston2024.bustracker.config.dto.busOperation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusOperationCreateDTO {
    private String busId;
    private String driverId;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private String routeId;
}