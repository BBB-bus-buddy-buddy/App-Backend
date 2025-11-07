package capston2024.bustracker.repository;

import capston2024.bustracker.domain.EventReward;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRewardRepository extends MongoRepository<EventReward, String> {

    // 이벤트 ID로 상품 목록 조회 (등급순 정렬)
    @Query("{'eventId.$id': ?0}")
    List<EventReward> findByEventIdOrderByRewardGrade(String eventId);

    // 재고가 남아있는 상품만 조회
    @Query("{'eventId.$id': ?0, 'remainingQuantity': {$gt: 0}}")
    List<EventReward> findAvailableRewardsByEventId(String eventId);
}
