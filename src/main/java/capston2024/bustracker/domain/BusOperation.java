package capston2024.bustracker.domain;

import com.mongodb.DBRef;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "busOperations")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BusOperation {

    @Id
    private String id;

    private String operationId;       // 운행 고유 ID
    private DBRef busId;              // 버스 참조
    private DBRef driverId;           // 기사 참조
    private LocalDateTime scheduledStart;  // 예정 시작 시간
    private LocalDateTime scheduledEnd;    // 예정 종료 시간
    private LocalDateTime actualStart;     // 실제 시작 시간
    private LocalDateTime actualEnd;       // 실제 종료 시간
    private OperationStatus status;        // 운행 상태
    private String organizationId;         // 조직 ID
    private Integer totalPassengers;       // 총 승객 수
    private Integer totalStopsCompleted;   // 완료된 정류장 수
    private DBRef routeId;                // 라우트 참조

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum OperationStatus {
        SCHEDULED,      // 예정됨
        IN_PROGRESS,    // 운행 중
        COMPLETED,      // 완료됨
        CANCELLED       // 취소됨
    }
}