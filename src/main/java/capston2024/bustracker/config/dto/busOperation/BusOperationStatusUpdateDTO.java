package capston2024.bustracker.config.dto.busOperation;

import capston2024.bustracker.domain.BusOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusOperationStatusUpdateDTO {
    private String operationId;
    private BusOperation.OperationStatus status;
    private Integer currentPassengers;
    private Integer currentStopsCompleted;
}