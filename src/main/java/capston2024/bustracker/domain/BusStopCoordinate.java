package capston2024.bustracker.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
@Getter
@Setter
@Document(collection = "Station")
public class BusStopCoordinate {
    private Long id;

    private String name;
    private double x;
    private double y;
    public BusStopCoordinate(String name, double x, double y){
        this.name = name;
        this.x = x;
        this.y = y;
    }
}
