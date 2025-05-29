// src/main/java/capston2024/bustracker/domain/BusOperation.java
package capston2024.bustracker.domain;

import com.mongodb.DBRef;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "BusOperation")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusOperation {

    @Id
    private String id;

    private String operationId;

    private DBRef busId;

    private DBRef driverId;

    private LocalDateTime scheduledStart;

    private LocalDateTime scheduledEnd;

    private LocalDateTime actualEnd;

    private String status; // SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED

    private String organizationId;

    // 반복 관련 필드 추가
    private boolean isRecurring;

    private Integer recurringWeeks;

    private String parentOperationId; // 반복 생성된 경우 원본 ID

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}