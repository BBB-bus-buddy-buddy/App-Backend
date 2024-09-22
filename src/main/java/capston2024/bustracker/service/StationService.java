package capston2024.bustracker.service;

import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
        log.info("MongoDB에서 데이터를 불러들이는 중...");
        try {
            return stationRepository.findAll();
        } catch (RuntimeException e){
            throw new BusinessException(ErrorCode.GENERAL_ERROR);
        }
    }
}
