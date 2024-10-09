package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.BusDTO;
import capston2024.bustracker.config.dto.BusRegisterDTO;
import capston2024.bustracker.config.dto.BusSeatDTO;
import capston2024.bustracker.config.dto.LocationDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@AllArgsConstructor
public class BusService {

    private final BusRepository busRepository;
    private final StationService stationService;

    public boolean createBus(BusRegisterDTO busRegisterDTO) {
        if(stationService.isValidStationNames(busRegisterDTO.getStationNames())){
            Bus bus = Bus.builder()
                    .busNumber(busRegisterDTO.getBusNumber())
                    .stationsNames(busRegisterDTO.getStationNames())
                    .totalSeats(busRegisterDTO.getTotalSeats())
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
            Bus bus = busRepository.findById(busDTO.getId()).orElseThrow(()->new ResourceNotFoundException("버스를 찾을 수 없습니다."));
            bus.setStationsNames(busDTO.getStationsNames());
            bus.setBusNumber(bus.getBusNumber());
            bus.setTotalSeats(bus.getTotalSeats());
            return true;
    }

    // 4. 모든 버스 조회
    public List<Bus> getAllBuses() {
        return busRepository.findAll();
    }

    // 특정 버스 조회
    public Bus getBusByNumber(String busNumber) {
        return busRepository.findBusByBusNumber(busNumber).orElseThrow(()->new ResourceNotFoundException("버스를 찾을 수 없습니다."));
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
        if (parts.length < 2) {
            throw new IllegalArgumentException("CSV 형식이 알맞지 않습니다.");
        }
        Bus bus = getBusByNumber(parts[0]);
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


