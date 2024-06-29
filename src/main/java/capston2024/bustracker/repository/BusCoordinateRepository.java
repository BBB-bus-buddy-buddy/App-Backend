package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusCoordinateDomain;

import java.util.List;
import java.util.Optional;

public interface BusCoordinateRepository {
    BusCoordinateDomain addBus(BusCoordinateDomain coordinate);
    Optional<BusCoordinateDomain> findById(int id);
    Optional<BusCoordinateDomain> findByName(String name);
    Optional<BusCoordinateDomain> findByCoordinate(double x, double y);
    List<BusCoordinateDomain> findAll();
}
