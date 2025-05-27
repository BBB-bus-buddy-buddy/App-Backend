package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusTrackingEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusTrackingEventRepository extends MongoRepository<BusTrackingEvent, String> {

    // 특정 운행의 최신 이벤트 조회
    Optional<BusTrackingEvent> findTopByOperationIdOrderByTimestampDesc(String operationId);

    // 특정 운행의 특정 타입의 최신 이벤트 조회
    Optional<BusTrackingEvent> findTopByOperationIdAndEventTypeOrderByTimestampDesc(
            String operationId, String eventType);

    // 특정 시간 범위의 이벤트 조회
    List<BusTrackingEvent> findByOperationIdAndTimestampBetween(
            String operationId, Instant start, Instant end);

    // 특정 버스의 현재 운행 이벤트
    List<BusTrackingEvent> findByBusIdAndTimestampAfterOrderByTimestampDesc(
            String busId, Instant after);

    // 사용자의 탑승/하차 이벤트 조회
    List<BusTrackingEvent> findByUserIdAndEventTypeInAndTimestampAfter(
            String userId, List<String> eventTypes, Instant after);

    // 조직의 실시간 이벤트 조회
    @Query("{'organizationId': ?0, 'timestamp': {$gte: ?1}, 'eventType': {$in: ?2}}")
    List<BusTrackingEvent> findRecentEventsByOrganization(
            String organizationId, Instant after, List<String> eventTypes);

    // 오래된 이벤트 삭제
    long deleteByTimestampBefore(Instant timestamp);

    // 특정 시간 이후 이벤트 수 카운트
    long countByTimestampAfter(Instant timestamp);
}