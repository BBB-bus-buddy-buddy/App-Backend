package capston2024.bustracker.repository;

import capston2024.bustracker.domain.EventMission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventMissionRepository extends MongoRepository<EventMission, String> {

    // 이벤트 ID로 미션 목록 조회
    @Query("{'eventId.$id': ?0}")
    List<EventMission> findByEventIdOrderByOrder(String eventId);

    // 필수 미션만 조회
    @Query("{'eventId.$id': ?0, 'isRequired': true}")
    List<EventMission> findRequiredMissionsByEventId(String eventId);
}
