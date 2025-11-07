package capston2024.bustracker.repository;

import capston2024.bustracker.domain.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends MongoRepository<Event, String> {

    // 활성화된 이벤트 조회
    List<Event> findByIsActiveTrueOrderByCreatedAtDesc();

    // 조직별 이벤트 조회
    List<Event> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

    // 조직별 활성화된 이벤트 조회
    Optional<Event> findFirstByOrganizationIdAndIsActiveTrueOrderByCreatedAtDesc(String organizationId);

    // 기간으로 이벤트 조회
    List<Event> findByStartDateBeforeAndEndDateAfter(LocalDateTime now1, LocalDateTime now2);
}
