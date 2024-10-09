package capston2024.bustracker.service;


import capston2024.bustracker.domain.Station;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import capston2024.bustracker.repository.StationRepository;
import capston2024.bustracker.repository.UserRepository;
import com.mongodb.DBRef;
import com.mongodb.client.result.UpdateResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StationRepository stationRepository;
    private final MongoTemplate mongoTemplate;

    //내 정류장 조회
    public List<Station> getMyStationList(String email) {
        log.warn("Email {} 사용자의 내 정류장 목록 조회를 시작합니다.", email);
        // 사용자 조회

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 사용자의 내 정류장 목록 반환
        return user.getMyStations();
    }


    public boolean addMyStation(String email, String stationId) {
        log.info("{} 사용자의 내 정류장 목록에 {}를 추가 중...", email, stationId);

        // 정류장 존재 여부 확인
        if (!stationRepository.existsById(stationId)) {
            log.warn("존재하지 않는 정류장입니다: {}", stationId);
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }

        // 사용자 존재 여부와 중복 정류장 확인을 동시에 수행
        Query query = new Query(Criteria.where("email").is(email)
                .and("myStations").not().elemMatch(Criteria.where("$id").is(stationId)));
        Update update = new Update().addToSet("myStations", new DBRef("stations", stationId));

        UpdateResult result = mongoTemplate.updateFirst(query, update, User.class);

        if (result.getMatchedCount() == 0) {
            log.warn("사용자를 찾을 수 없거나 이미 등록된 정류장입니다: {} - {}", email, stationId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (result.getModifiedCount() == 0) {
            log.warn("이미 {} 사용자가 등록한 정류장입니다: {}", email, stationId);
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY);
        }

        log.info("사용자의 내 정류장 목록에 {}를 성공적으로 추가했습니다.", stationId);
        return true;
    }

    // 내 정류장 삭제
    public boolean deleteMyStation(String email, String stationId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Station station = findByStationWithException(stationId);

        if (user.getMyStations().contains(station))
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);

        List<Station> list = user.getMyStations();
        list.remove(station);
        user.setMyStations(list);
        return true;
    }

    // 정류장 탐색 + 예외처리
    private Station findByStationWithException(String stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

}
