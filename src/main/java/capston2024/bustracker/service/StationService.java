package capston2024.bustracker.service;

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

    // 정류장 이름으로 검색
    public List<Station> getStationName(String stationName) {
        log.info("{} 정류장을 찾는 중입니다....", stationName);
        return stationRepository.findByNameContainingIgnoreCase(stationName);
    }

    // 모든 정류장 조회
    public List<Station> getAllStations() {
        log.info("모든 정류장을 불러들이는 중...");
        return stationRepository.findAll();
    }

    // 특정 ID로 정류장 조회
    public Station getStationById(String id) {
        log.info("ID {}로 정류장을 찾는 중입니다....", id);
        return stationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    // 새로운 정류장 추가 - 유효성 검사 포함
    public Station createStation(Station station) {
        log.info("새로운 정류장 추가 중: {}", station.getName());

        // 중복된 정류장이 존재하는지 확인
        if (stationRepository.findByName(station.getName()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY);
        }

        return stationRepository.save(station);
    }

    // 정류장 업데이트 - 유효성 검사 포함
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

    // 정류장 이름 목록을 받아 유효성 검사
    public boolean isValidStationNames(List<String> stationNames) {
        log.info("정류장 이름 목록의 유효성 검사 중...");

        for (String stationName : stationNames) {
            // 정류장 이름을 통해 해당 정류장 객체를 찾음
            Optional<Station> stationOpt = stationRepository.findByName(stationName);

            if (stationOpt.isEmpty()) {
                log.warn("유효하지 않은 정류장 발견: {}", stationName);
                return false;
            }
        }

        return true;
    }
}
