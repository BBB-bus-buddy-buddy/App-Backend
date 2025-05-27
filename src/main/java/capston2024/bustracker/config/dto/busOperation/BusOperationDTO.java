package capston2024.bustracker.config.dto.busOperation;

import capston2024.bustracker.domain.BusOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusOperationDTO {
    private String id;
    private String operationId;
    private String busNumber;
    private String busRealNumber;
    private String driverName;
    private String driverEmail;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;
    private BusOperation.OperationStatus status;
    private String organizationId;
    private Integer totalPassengers;
    private Integer totalStopsCompleted;
    private String routeName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}