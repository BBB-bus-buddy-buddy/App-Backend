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

    // 실시간 위치 정보
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;

    // 마지막 위치 업데이트 시간
    private Instant lastLocationUpdate;

    // 이전 정류장 인덱스 (라우트 상에서의 위치)
    private Integer prevStationIdx;

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

    /**
     * 위치 업데이트
     */
    public void updateLocation(double latitude, double longitude) {
        this.location = new GeoJsonPoint(longitude, latitude);
        this.lastLocationUpdate = Instant.now();
    }

    /**
     * 이전 정류장 인덱스 업데이트
     */
    public void updatePrevStationIdx(int stationIdx) {
        this.prevStationIdx = stationIdx;
    }

    /**
     * 운행 가능 여부 확인
     */
    public boolean isOperational() {
        return this.operationalStatus == OperationalStatus.ACTIVE &&
                (this.serviceStatus == ServiceStatus.NOT_IN_SERVICE ||
                        this.serviceStatus == ServiceStatus.IN_SERVICE);
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