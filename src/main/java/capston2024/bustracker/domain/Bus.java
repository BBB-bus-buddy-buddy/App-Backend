package capston2024.bustracker.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "Bus")
@Getter @Setter
@AllArgsConstructor // 모든 필드를 받는 생성자 생성
@NoArgsConstructor // 기본 생성자 생성
public class Bus {

    @Id
    private String id; // MongoDB에서 자동 생성될 _id
    private int seat;
    private GeoJsonPoint location; // 좌표 정보 (GeoJSON 형식)
    private Instant timestamp;
}
