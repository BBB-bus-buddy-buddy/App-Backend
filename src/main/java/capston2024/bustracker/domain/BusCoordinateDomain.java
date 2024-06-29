package capston2024.bustracker.domain;

public class BusCoordinateDomain extends BusStopCoordinateDomain {
    private int busNum;
    public BusCoordinateDomain(String name, double x, double y) {
        super(name, x, y);
    }

    public BusCoordinateDomain(String name, double x, double y, int busNum) {
        super(name, x, y);
        this.busNum = busNum;
    }
}
