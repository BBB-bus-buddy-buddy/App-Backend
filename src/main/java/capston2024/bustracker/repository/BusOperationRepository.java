package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusOperation;
import com.mongodb.DBRef;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusOperationRepository extends MongoRepository<BusOperation, String> {

    // 조직별 운행 조회
    List<BusOperation> findByOrganizationId(String organizationId);

    // 운행 상태별 조회
    List<BusOperation> findByOrganizationIdAndStatus(String organizationId, BusOperation.OperationStatus status);

    // 버스별 운행 조회
    @Query("{'busId.$id': ?0}")
    List<BusOperation> findByBusId(String busId);

    // **누락된 메서드 추가 - 버스별 특정 상태 운행 조회**
    @Query("{'busId.$id': ?0, 'status': {$in: ?1}}")
    List<BusOperation> findByBusIdAndStatusIn(String busId, List<BusOperation.OperationStatus> statuses);

    // 기사별 운행 조회
    @Query("{'driverId.$id': ?0}")
    List<BusOperation> findByDriverId(String driverId);

    // 운행 ID로 조회
    Optional<BusOperation> findByOperationId(String operationId);

    // 특정 기간 내 운행 조회
    List<BusOperation> findByOrganizationIdAndScheduledStartBetween(
            String organizationId, LocalDateTime start, LocalDateTime end);

    // 진행 중인 운행 조회
    List<BusOperation> findByOrganizationIdAndStatusIn(
            String organizationId, List<BusOperation.OperationStatus> statuses);

    // 기사의 오늘 운행 스케줄 조회
    @Query("{'driverId.$id': ?0, 'scheduledStart': {$gte: ?1, $lt: ?2}}")
    List<BusOperation> findTodayOperationsByDriverId(String driverId, LocalDateTime startOfDay, LocalDateTime endOfDay);
}