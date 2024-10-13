package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.BusDTO;
import capston2024.bustracker.config.dto.BusRegisterDTO;
import capston2024.bustracker.config.dto.BusSeatDTO;
import capston2024.bustracker.config.dto.LocationDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusRepository;
import com.mongodb.DBRef;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class BusService {

    private final BusRepository busRepository;
    private final StationService stationService;
    private final MongoOperations mongoOperations;

    public boolean createBus(BusRegisterDTO busRegisterDTO) {
        List<String> stationNames = busRegisterDTO.getStationNames();

        if (stationService.isValidStationNames(stationNames)) {
            List<DBRef> stationRefs = stationNames != null && !stationNames.isEmpty()
                    ? stationNames.stream()
                    .map(name -> new DBRef("station", stationService.findStationIdByName(name)))
                    .collect(Collectors.toList())
                    : new ArrayList<>();

            Bus bus = Bus.builder()
                    .busNumber(busRegisterDTO.getBusNumber())
                    .stations(stationRefs)
                    .totalSeats(busRegisterDTO.getTotalSeats())
                    .occupiedSeats(0)
                    .availableSeats(busRegisterDTO.getTotalSeats())
                    .location(new GeoJsonPoint(35.495299450684456, 129.4172414821444))
                    .timestamp(Instant.now())
                    .build();

            busRepository.save(bus);
            return true;
        }
        return false;
    }

    // 2. 버스 삭제
    public boolean removeBus(String busNumber) {
        busRepository.delete(busRepository.findBusByBusNumber(busNumber).orElseThrow(()-> new ResourceNotFoundException("버스를 찾을 수 없습니다.")));
        return true;
    }

    // 3. 버스 수정 - 전체 버스 수정 사항
    // 수정 사항 : 버스 정류장 이름, 버스 번호, 버스 전체 좌석
    public boolean modifyBus(BusDTO busDTO) {
        List<String> stationNames = busDTO.getStationNames();

        if (stationService.isValidStationNames(stationNames)) {
            Bus bus = busRepository.findById(busDTO.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다."));

            List<DBRef> stationRefs = stationNames != null && !stationNames.isEmpty()
                    ? stationNames.stream()
                    .map(name -> new DBRef("station", stationService.findStationIdByName(name)))
                    .collect(Collectors.toList())
                    : new ArrayList<>();

            bus.setStations(stationRefs);
            bus.setBusNumber(busDTO.getBusNumber());
            bus.setTotalSeats(busDTO.getTotalSeats());
            return true;
        }
        return false;
    }

    // 4. 모든 버스 조회
    public List<Bus> getAllBuses() {
        return busRepository.findAll();
    }

    // 특정 버스 조회
    public Bus getBusByNumber(String busNumber) {
        return busRepository.findBusByBusNumber(busNumber).orElseThrow(()->new ResourceNotFoundException("버스를 찾을 수 없습니다."));
    }

    public List<Bus> getBusesByStationId(String stationId) {
        log.info("해당 정류장이 포함된 버스를 찾는 중.. {}", stationId);
        Query query = new Query(Criteria.where("stations").is(new DBRef("station", stationId)));
        log.info("해당 정류장이 포함된 버스를 찾는 중.. {}", query);
        List<Bus> bus = mongoOperations.find(query, Bus.class);
        log.info("버스를 찾았습니다 : {}", bus);
        return bus;
    }

    /**
     * 웹 소켓에서 받아오는 서비스 - 버스 위치 정보, 버스 좌석 정보
     * @param csvData ( busNumber, location(lat, lst) )
     * @return CompletableFuture.xFuture(e)
     */
    @Async("taskExecutor")
    public CompletableFuture<Bus> processBusLocationAsync(String csvData) {
        try {
            Bus bus = parseCsvToBus(csvData);
            return CompletableFuture.completedFuture(bus);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("taskExecutor")
    public CompletableFuture<Bus> processBusSeatAsync(String csvData) {
        try {
            Bus bus = parseCsvToBusSeatsInfo(csvData);
            return CompletableFuture.completedFuture(bus);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    private Bus parseCsvToBusSeatsInfo(String csvData) {
        String[] parts = csvData.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("CSV 형식이 알맞지 않습니다.");
        }
        Bus bus = getBusByNumber(parts[0]); //버스 번호 String
        bus.setOccupiedSeats(Integer.parseInt(parts[1])); // 버스 현재 차지된 좌석
        bus.setAvailableSeats(bus.getTotalSeats()-bus.getOccupiedSeats());
        return bus;
    }

    //파싱된 버스 위치정보는 무조건 modify 여야한다.
    private Bus parseCsvToBus(String csvData) {
        String[] parts = csvData.split(",");
        log.info("받은 csvData : {}", csvData);
        if (parts.length < 2) {
            throw new IllegalArgumentException("CSV 형식이 알맞지 않습니다.");
        }
        Bus bus = getBusByNumber(parts[0]);
        log.info("버스 객체 : {}", bus);
        bus.setLocation(new GeoJsonPoint(Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
        bus.setTimestamp(Instant.now());
        return bus;
    }

    public BusSeatDTO getBusSeatsByBusNumber(String busNumber) {
        Bus bus = getBusByNumber(busNumber);
        BusSeatDTO busSeatDTO = new BusSeatDTO();
        busSeatDTO.setAvailableSeats(bus.getAvailableSeats());
        busSeatDTO.setOccupiedSeats(bus.getOccupiedSeats());
        busSeatDTO.setTotalSeats(bus.getTotalSeats());
        return busSeatDTO;
    }

    public LocationDTO getBusLocationByBusNumber(String busNumber) {
        Bus bus = getBusByNumber(busNumber);
        LocationDTO locationDTO = new LocationDTO();
        locationDTO.setLatitude(bus.getLocation().getX());
        locationDTO.setLongitude(bus.getLocation().getY());
        locationDTO.setTimestamp(Instant.now());
        return locationDTO;
    }
}


