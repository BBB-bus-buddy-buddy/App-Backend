package capston2024.bustracker.service;


import capston2024.bustracker.domain.Station;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.repository.StationRepository;
import capston2024.bustracker.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StationRepository stationRepository;

    //내 정류장 조회
    public List<Station> getMyStationList(String email) {
        log.warn("Email {} 사용자의 내 정류장 목록 조회를 시작합니다.", email);
        // 사용자 조회

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 사용자의 내 정류장 목록 반환
        return user.getMyStations();
    }

    // 내 정류장 추가
    public boolean addMyStation(String email, String stationId) {
        log.info("{} 사용자의 내 정류장 목록에 {}를 추가 중...", email, stationId);
        // 사용자 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 정류장 조회
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        // 정류장 중복 확인
        if (user.getMyStations().contains(station)) {
            log.warn("이미 {} 사용자가 등록한 정류장입니다: {}", email, stationId);
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY);
        }
        // 정류장 추가
        user.getMyStations().add(station);
        return true;
    }

    // 내 정류장 삭제
    public boolean deleteMyStation(String email, String stationId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Station station = findByStationWithException(stationId);

        if (user.getMyStations().contains(station))
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);

        user.getMyStations().remove(station);
        return true;
    }

    // 정류장 탐색 + 예외처리
    private Station findByStationWithException(String stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

}
