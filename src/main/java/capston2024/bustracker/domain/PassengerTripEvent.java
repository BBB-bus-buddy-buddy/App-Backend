package capston2024.bustracker.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * 승객의 탑승/하차/위치 이벤트를 기록하는 도큐먼트.
 * 향후 혼잡도 예측이나 분석에 사용된다.
 */
@Document(collection = "PassengerTripEvent")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PassengerTripEvent {

    @Id
    private String id;
    private String userId;
    private String organizationId;
    private String busNumber;
    private String stationId;
    private EventType eventType;
    private double latitude;
    private double longitude;
    private Double distanceToBus;
    private Double estimatedBusSpeed;
    private long timestamp;
    private Map<String, Object> metadata;

    public enum EventType {
        LOCATION,
        BOARD,
        ALIGHT
    }
}
