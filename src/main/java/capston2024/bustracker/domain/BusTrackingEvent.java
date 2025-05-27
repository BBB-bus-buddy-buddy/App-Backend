package capston2024.bustracker.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "busTrackingEvents")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BusTrackingEvent {

    @Id
    private String id;

    @Indexed
    private Instant timestamp;

    private Map<String, Object> metadata;

    private String eventType; // LOCATION_UPDATE, BOARDING, ALIGHTING, STATUS_CHANGE

    private String busId;

    private String operationId;

    private String organizationId;

    private SeatStatus seatStatus;

    private GeoJsonPoint location;

    // 추가 필드
    private String userId; // 탑승/하차 이벤트 시 사용자 ID

    private Double speed; // 버스 속도

    private Double heading; // 버스 방향

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatStatus {
        private int occupied;
        private int available;
        private int total;
    }
}