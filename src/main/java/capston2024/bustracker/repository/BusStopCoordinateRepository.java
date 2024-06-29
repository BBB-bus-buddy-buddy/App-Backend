package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusStopCoordinateDomain;

import java.util.List;
import java.util.Optional;

public interface BusStopCoordinateRepository {
     BusStopCoordinateDomain addCoordinate(BusStopCoordinateDomain coordinate);
     Optional<BusStopCoordinateDomain> findByName(String name);
     Optional<BusStopCoordinateDomain> findByCoordinate(double x, double y);
     List<BusStopCoordinateDomain> findAll();
}
