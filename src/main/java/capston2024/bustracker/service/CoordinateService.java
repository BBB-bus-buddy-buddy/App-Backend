package capston2024.bustracker.service;

import capston2024.bustracker.domain.BusCoordinate;
import capston2024.bustracker.domain.BusStopCoordinate;
import capston2024.bustracker.repository.BusCoordinateRepository;
import capston2024.bustracker.repository.BusStopCoordinateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CoordinateService {
    private final BusStopCoordinateRepository busStopRepo;
    private final BusCoordinateRepository busRepo;

    public CoordinateService(BusStopCoordinateRepository busStopRepo, BusCoordinateRepository busRepo) {
        this.busStopRepo = busStopRepo;
        this.busRepo = busRepo;
    }

    // get All coordinate
    public List<BusStopCoordinate> getAllBusStops(String uid) {
        return busStopRepo.findByUid(uid);
    }

    // get User's coordinate
    public List<BusStopCoordinate> getUserBusStops(String userId) {
        return busStopRepo.findByUserId(userId);
    }

    // getCoordinate
    public List<BusCoordinate> getBusCoordinates(String uid) {
        return busRepo.findByUid(uid);
    }
}
