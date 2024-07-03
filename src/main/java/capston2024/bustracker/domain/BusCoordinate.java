package capston2024.bustracker.domain;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "bus")
public class BusCoordinate extends BusStopCoordinate {
    private int busNum;
    public BusCoordinate(String name, double x, double y) {
        super(name, x, y);
    }

    public BusCoordinate(String name, double x, double y, int busNum) {
        super(name, x, y);
        this.busNum = busNum;
    }
}
