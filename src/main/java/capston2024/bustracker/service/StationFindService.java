package capston2024.bustracker.service;

import capston2024.bustracker.domain.Station;
import capston2024.bustracker.repository.StationRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class StationFindService {

    @Autowired
    private StationRepository stationRepository;

    // 특정 name에 해당하는 정류장의 정보를 반환
    public List<Station> getStationByName(String name) {
        log.info("{} 정류장을 찾는 중입니다....", name);
        return stationRepository.findByNameContaining(name);
    }

    // 모든 정류장을 가져옴
    public List<Station> getAllStations() {
        log.info("MongoDB에서 데이터를 불러들이는 중...");
        return stationRepository.findAll();
    }
}
