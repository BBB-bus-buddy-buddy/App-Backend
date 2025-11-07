package capston2024.bustracker.repository;

import capston2024.bustracker.domain.EventParticipation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventParticipationRepository extends MongoRepository<EventParticipation, String> {

    // 이벤트와 사용자로 참여 기록 조회
    @Query("{'eventId.$id': ?0, 'userId.$id': ?1}")
    Optional<EventParticipation> findByEventIdAndUserId(String eventId, String userId);

    // 사용자별 모든 참여 기록
    @Query("{'userId.$id': ?0}")
    List<EventParticipation> findByUserId(String userId);

    // 이벤트별 모든 참여 기록
    @Query("{'eventId.$id': ?0}")
    List<EventParticipation> findByEventId(String eventId);

    // 이벤트별 뽑기 완료한 참여자 수
    @Query(value = "{'eventId.$id': ?0, 'hasDrawn': true}", count = true)
    long countDrawnParticipantsByEventId(String eventId);
}
