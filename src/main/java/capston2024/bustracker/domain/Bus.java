package capston2024.bustracker.domain;

import com.mongodb.DBRef;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;

@Document(collection = "buses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bus {
    @Id
    private String id;

    private String busNumber;           // 시스템 생성 버스 번호
    private String busRealNumber;       // 실제 버스 번호 (선택사항)
    private String organizationId;      // 조직 ID
    private int totalSeats;            // 총 좌석 수
    private DBRef routeId;             // 노선 참조

    // 운영 상태
    private OperationalStatus operationalStatus = OperationalStatus.ACTIVE;
    private ServiceStatus serviceStatus = ServiceStatus.NOT_IN_SERVICE;

    // 위치 정보 (실시간 업데이트)
    private GeoJsonPoint location;      // 현재 위치
    private Instant lastLocationUpdate; // 마지막 위치 업데이트 시간

    // 운행 정보 (Bus에서 관리하는 현재 상태)
    private String currentStationName;  // 현재 정류장명
    private Integer prevStationIdx;     // 이전 정류장 인덱스
    private String prevStationId;       // 이전 정류장 ID
    private Instant lastStationTime;    // 마지막 정류장 도착 시간

    // 생성/수정 시간
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum OperationalStatus {
        ACTIVE,      // 활성
        INACTIVE,    // 비활성
        MAINTENANCE, // 정비중
        RETIRED      // 퇴역
    }

    public enum ServiceStatus {
        NOT_IN_SERVICE, // 운행 전
        IN_SERVICE,     // 운행 중
        OUT_OF_ORDER,   // 고장
        CLEANING        // 청소 중
    }

    @Override
    public String toString() {
        return "Bus{" +
                "id='" + id + '\'' +
                ", busNumber='" + busNumber + '\'' +
                ", busRealNumber='" + busRealNumber + '\'' +
                ", organizationId='" + organizationId + '\'' +
                ", totalSeats=" + totalSeats +
                ", operationalStatus=" + operationalStatus +
                ", serviceStatus=" + serviceStatus +
                ", location=" + (location != null ? "(" + location.getX() + ", " + location.getY() + ")" : "null") +
                ", prevStationIdx=" + prevStationIdx +
                '}';
    }
}