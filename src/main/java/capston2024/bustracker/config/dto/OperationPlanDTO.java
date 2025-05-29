// src/main/java/capston2024/bustracker/config/dto/OperationPlanDTO.java
package capston2024.bustracker.config.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationPlanDTO {

    private String id;

    private String operationId;

    private String busId;

    private String busNumber;

    private String busRealNumber;

    private String driverId;

    private String driverName;

    private String routeId;

    private String routeName;

    private LocalDate operationDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private String status;

    private boolean isRecurring;

    private Integer recurringWeeks;

    private String organizationId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}