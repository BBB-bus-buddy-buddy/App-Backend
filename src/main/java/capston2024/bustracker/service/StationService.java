package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.CreateStationDTO;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;
    private final AuthService authService;
    private final SchoolService schoolService;


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

    // 새로운 정류장 추가
    public Station createStation(OAuth2User userPrincipal, CreateStationDTO createStationDTO) {
        log.info("새로운 정류장 추가 중: {}", createStationDTO.getName());

        // OAuth2 사용자 정보 가져오기
        Map<String, Object> userInfo = authService.getUserDetails(userPrincipal);
        String organizationId = (String) userInfo.get("organizationId"); // 사용자 소속 정보

        // 중복된 정류장이 있는지 확인
        if (stationRepository.findByName(createStationDTO.getName()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "이미 존재하는 정류장입니다.");
        }

        // 새로운 정류장 생성
        Station newStation = Station.builder()
                .name(createStationDTO.getName())
                .location(new GeoJsonPoint(createStationDTO.getLatitude(), createStationDTO.getLongitude()))
                .organizationId(organizationId) // 사용자 소속 정보 추가
                .build();

        return stationRepository.save(newStation);
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
