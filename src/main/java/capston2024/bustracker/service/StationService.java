package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.BusRegisterDTO;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;

    // 특정 name에 해당하는 정류장의 정보를 반환
    public List<Station> getStation(String name) {
        log.info("{} 정류장을 찾는 중입니다....", name);
        try {
            return stationRepository.findByNameContaining(name);
        } catch (RuntimeException e){
            throw new BusinessException(ErrorCode.GENERAL_ERROR);
        }
    }

    // 모든 정류장을 가져옴
    public List<Station> getAllStations() {
        log.info("모든 정류장을 불러들이는 중...");
        try {
            return stationRepository.findAll();
        } catch (RuntimeException e){
            throw new BusinessException(ErrorCode.GENERAL_ERROR);
        }
    }

    // 특정 ID로 정류장 조회
    public Station getStationById(String id) {
        log.info("ID {}로 정류장을 찾는 중입니다....", id);
        return stationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    // 새로운 정류장 추가
    public Station createStation(Station station) {
        log.info("새로운 정류장 추가 중: {}", station.getName());
        if (stationRepository.findByName(station.getName()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY);
        }
        return stationRepository.save(station);
    }

    // 정류장 업데이트
    public Station updateStation(String id, Station updatedStation) {
        log.info("ID {}로 정류장 업데이트 중...", id);
        Station existingStation = getStationById(id);  // 존재 여부 확인
        existingStation.setName(updatedStation.getName());
        existingStation.setLocation(updatedStation.getLocation());
        existingStation.setOrganizationId(updatedStation.getOrganizationId());
        return stationRepository.save(existingStation);
    }

    // 정류장 삭제
    public void deleteStation(String id) {
        log.info("ID {}로 정류장 삭제 중...", id);
        Station station = getStationById(id);  // 존재 여부 확인
        stationRepository.delete(station);
    }

    // 정류정 유효성 검사
    protected boolean isValidStation(BusRegisterDTO busRegisterDTO) {
        List<String> stationNames = busRegisterDTO.getStationNames();
        for (String stationName : stationNames) {
            Optional<Station> station = stationRepository.findByName(stationName);
            if (station.isEmpty())
                return false;
        }
        return true;
    }
}
