package capston2024.bustracker.domain;

public class BusStopCoordinateDomain {
    private Long id;
    private String name;
    private double x;
    private double y;
    public BusStopCoordinateDomain(String name, double x, double y){
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
