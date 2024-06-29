package capston2024.bustracker.model;

public class BusCoordinateModel extends CoordinateModel{
    private int busNum;
    public BusCoordinateModel(String name, double x, double y) {
        super(name, x, y);
    }

    public BusCoordinateModel(String name, double x, double y, int busNum) {
        super(name, x, y);
        this.busNum = busNum;
    }
}
