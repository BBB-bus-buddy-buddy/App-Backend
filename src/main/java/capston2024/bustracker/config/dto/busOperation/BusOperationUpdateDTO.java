package capston2024.bustracker.config.dto.busOperation;

import capston2024.bustracker.domain.BusOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusOperationUpdateDTO {
    private String operationId;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;
    private BusOperation.OperationStatus status;
    private Integer totalPassengers;
    private Integer totalStopsCompleted;
}