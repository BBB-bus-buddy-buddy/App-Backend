package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.BusRegisterRequestDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.StationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@AllArgsConstructor
public class BusService {

    private final BusRepository busRepository;
    private final StationRepository stationRepository;
    private final StationService stationService;
    private final List<BusRegisterRequestDTO> busList = new ArrayList<>();


    public String createBus(BusRegisterRequestDTO busRegisterRequestDTO) {

        // 정류장이 모두 존재하는 정류장인지 확인 ( StationService 에서 해결 )
        List<String> stationNames = busRegisterRequestDTO.getStationNames();
        for (String stationName : stationNames) {
            Optional<Station> station = stationRepository.findByName(stationName);
            if (station.isEmpty()) {
                return "정류장: " + stationName + "은(는) 없는 정류장입니다.";
            }
        }
        Bus bus = Bus.builder()
                .busNumber(busRegisterRequestDTO.getBusNumber())
                .stationsNames(busRegisterRequestDTO.getStationNames())
                .build();
        busRepository.save(bus);
        return busRegisterRequestDTO.getBusNumber() + "번 버스가 성공적으로 등록되었습니다.";
    }

    // 2. 버스 삭제
    public String removeBus(String busNumber) {
        if(busRepository.existsBusByBusNumber(busNumber)){
            busRepository.delete(busRepository.findBusByBusNumber(busNumber).orElseThrow(()-> new BusinessException("버스를 찾을 수 없습니다.")));
            return "성공적으로 버스를 삭제하였습니다.";
        }
        return "버스를 찾을 수 없습니다.";
    }

    // 3. 버스 수정 - 정류장의 변동 사항이 생길 때만
    public String modifyBus(BusRegisterRequestDTO busRegisterRequestDTO) {
        for (BusRegisterRequestDTO bus : busList) {
            if (bus.getBusNumber().equals(busRegisterRequestDTO.getBusNumber())) {
                bus.setStationNames(busRegisterRequestDTO.getStationNames());
                return busRegisterRequestDTO.getBusNumber() + "번 버스가 성공적으로 수정되었습니다.";
            }
        }
        return "버스를 찾을 수 없습니다.";
    }

    // 4. 모든 버스 조회
    public List<Bus> getAllBuses() {
        return busRepository.findAll();
    }

    // 특정 버스 조회
    public Bus getBusByNumber(String busNumber) {
        return busRepository.existsBusByBusNumber(busNumber)
                ? busRepository.findBusByBusNumber(busNumber).orElseThrow(()->new BusinessException("버스를 찾을 수 없습니다."))
                : null;
    }

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
            throw new IllegalArgumentException("Invalid CSV format");
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
            throw new IllegalArgumentException("Invalid CSV format");
        }
        if(getBusByNumber(parts[0]) == null) throw new BusinessException("존재하지 않는 버스 번호 입니다.");
        Bus bus = getBusByNumber(parts[0]);
        bus.setLocation(new GeoJsonPoint(Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
        bus.setTimestamp(Instant.now());
        return bus;
    }
}


