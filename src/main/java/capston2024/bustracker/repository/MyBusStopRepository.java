package capston2024.bustracker.repository;

import capston2024.bustracker.domain.AuthDomain;
import capston2024.bustracker.domain.BusStopCoordinateDomain;

import java.util.List;

public interface MyBusStopRepository {
    BusStopCoordinateDomain addMyBusStop(BusStopCoordinateDomain busStop, AuthDomain member);
    BusStopCoordinateDomain removeMyBusStop(BusStopCoordinateDomain busStop, AuthDomain member);
    List<BusStopCoordinateDomain> findAll();
}
