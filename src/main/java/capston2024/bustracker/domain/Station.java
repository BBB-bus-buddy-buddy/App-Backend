package capston2024.bustracker.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

@Document(collection = "Station")
@Getter @Setter
@AllArgsConstructor // 모든 필드를 받는 생성자 생성
@NoArgsConstructor  // 기본 생성자 생성
@Builder
public class Station {

    @Id
    private String id;
    private String name;
    private GeoJsonPoint location;
    private String organizationId;

    @Override
    public String toString() {
        return "Station{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", location=" + location +
                ", organizationId='" + organizationId + '\'' +
                '}';
    }
}
