package capston2024.bustracker.domain;

import com.mongodb.DBRef;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "Bus")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Bus {

    @Id
    private String id;

    @Indexed(unique = true)
    private String busNumber;

    private String busRealNumber;

    @NonNull
    private String organizationId;

    private int totalSeats;

    private DBRef routeId;  // 기본 할당 라우트

    // 새로운 상태 관리 필드
    private OperationalStatus operationalStatus;  // 운영 상태
    private ServiceStatus serviceStatus;          // 서비스 상태

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // 운영 상태 enum
    public enum OperationalStatus {
        ACTIVE,      // 활성 (운영 가능)
        INACTIVE,    // 비활성 (일시 중단)
        MAINTENANCE, // 정비 중
        RETIRED      // 퇴역
    }

    // 서비스 상태 enum
    public enum ServiceStatus {
        NOT_IN_SERVICE,  // 비운행
        IN_SERVICE,      // 운행 중
        OUT_OF_ORDER,    // 고장
        CLEANING         // 청소 중
    }

    @Override
    public String toString() {
        return "Bus{" +
                "id='" + id + '\'' +
                ", busNumber='" + busNumber + '\'' +
                ", busRealNumber='" + busRealNumber + '\'' +
                ", organizationId='" + organizationId + '\'' +
                ", operationalStatus=" + operationalStatus +
                ", serviceStatus=" + serviceStatus +
                '}';
    }
}