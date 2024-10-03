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

@Service
@Slf4j
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StationRepository stationRepository;


    // 내 정류장 추가
    public boolean addMyStation(String userId, String stationId) {
        log.info("{} 사용자의 내 정류장 목록에 {}를 추가 중...", userId, stationId);
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 정류장 조회
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        // 정류장 중복 확인
        if (user.getMyStations().contains(station)) {
            log.warn("이미 {} 사용자가 등록한 정류장입니다: {}", userId, stationId);
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY);
        }
        // 정류장 추가
        user.getMyStations().add(station);
        userRepository.save(user);
        return true;
    }

    // 내 정류장 삭제
    public boolean deleteMyStation(String userId, String stationId) {
        User user = findByUserWithException(userId);
        Station station = findByStationWithException(stationId);

        if (user.getMyStations().contains(station))
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);

        user.getMyStations().remove(station);
        userRepository.save(user);
        return true;
    }

    // 유저 탐색 + 예외처리
    private User findByUserWithException(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
    // 정류장 탐색 + 예외처리
    private Station findByStationWithException(String stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

}
