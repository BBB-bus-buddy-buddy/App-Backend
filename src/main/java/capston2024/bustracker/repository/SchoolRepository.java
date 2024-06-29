package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusCoordinateDomain;
import capston2024.bustracker.domain.BusStopCoordinateDomain;
import capston2024.bustracker.domain.SchoolDomain;

public interface SchoolRepository {
    SchoolDomain addSchool(SchoolDomain school);
    boolean isInVerify(String code);
    BusStopCoordinateDomain addBusStop(SchoolDomain school, BusStopCoordinateDomain busStop);
    BusCoordinateDomain addBus(SchoolDomain school, BusCoordinateDomain bus);
}
