package capston2024.bustracker.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "Bus")
@Getter
@Setter
public class BusCoordinate {
    @Id
    private String id;
    private String busId;
    private double latitude;
    private double longitude;
    private Instant timestamp;
}
