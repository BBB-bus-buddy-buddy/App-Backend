package capston2024.bustracker.domain;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "bus-stop")
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }
}
