package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.BusRegisterRequestDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.StationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@AllArgsConstructor
public class BusService {

    private final BusRepository busRepository;
    private final StationRepository stationRepository;
    private final KakaoApiService kakaoApiService;

    public String createBus(BusRegisterRequestDTO busRegisterRequestDTO) {
        // 중복된 버스 번호 검사
        if (busRepository.existsBusByBusNumber(busRegisterRequestDTO.getBusNumber())) {
            return "이미 있는 버스 번호입니다.";
        }

        // 정류장이 모두 존재하는 정류장인지 확인(작업중)
        List<String> stationNames = busRegisterRequestDTO.getStationNames();
        for (String stationName : stationNames) {
            Optional<Station> station = stationRepository.findByName(stationName);
            if (station.isEmpty()) {
                return "정류장: " + stationName + "은(는) 없는 정류장입니다.";
            }
        }

        Bus bus = new Bus();
        bus.setBusNumber(busRegisterRequestDTO.getBusNumber());
        bus.setStationsNames(busRegisterRequestDTO.getStationNames());
        busRepository.save(bus);
        return busRegisterRequestDTO.getBusNumber() + "번 버스가 성공적으로 등록되었습니다.";
    }

    public void editBus() {

    }

    public void removeBus() {

    }

    @Async("taskExecutor")
    public CompletableFuture<Bus> processBusLocationAsync(String csvData) {
        try {
            Bus bus = parseCsvToBus(csvData);
            Bus savedBus = busRepository.save(bus);
            return CompletableFuture.completedFuture(savedBus);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Bus parseCsvToBus(String csvData) {
        String[] parts = csvData.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid CSV format");
        }
        return Bus.builder()
                .location(new GeoJsonPoint(Double.parseDouble(parts[1]), Double.parseDouble(parts[0])))
                .timestamp(Instant.now())
                .build();
    }

    /** 버스의 좌석 수를 업데이트하는 메소드
     * @param busId
     * @param occupiedSeats
     * @return
     */
    public boolean updateBusSeats(String busId, int occupiedSeats) {
        Optional<Bus> busOptional = busRepository.findById(busId);

        if (busOptional.isPresent()) {
            Bus bus = busOptional.get();
            bus.setOccupiedSeats(occupiedSeats);  // 버스의 앉은 좌석 수 업데이트
            busRepository.save(bus);
            return true;
        }
        return false;
    }
}


