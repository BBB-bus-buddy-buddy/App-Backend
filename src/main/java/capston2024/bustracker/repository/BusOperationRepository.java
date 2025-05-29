// src/main/java/capston2024/bustracker/repository/BusOperationRepository.java
package capston2024.bustracker.repository;

import capston2024.bustracker.domain.BusOperation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusOperationRepository extends MongoRepository<BusOperation, String> {

    // 조직별 특정 날짜의 운행 일정 조회
    List<BusOperation> findByOrganizationIdAndScheduledStartBetween(
            String organizationId, LocalDateTime start, LocalDateTime end);

    // 조직별 모든 운행 일정 조회
    List<BusOperation> findByOrganizationIdOrderByScheduledStartDesc(String organizationId);

    // 특정 버스의 운행 일정 조회
    @Query("{'busId.$id': ?0, 'organizationId': ?1}")
    List<BusOperation> findByBusIdAndOrganizationId(String busId, String organizationId);

    // 특정 기사의 운행 일정 조회
    @Query("{'driverId.$id': ?0, 'organizationId': ?1}")
    List<BusOperation> findByDriverIdAndOrganizationId(String driverId, String organizationId);

    // 운행 ID와 조직 ID로 조회
    Optional<BusOperation> findByOperationIdAndOrganizationId(String operationId, String organizationId);

    // ID와 조직 ID로 조회
    Optional<BusOperation> findByIdAndOrganizationId(String id, String organizationId);

    // 중복 일정 체크
    @Query("{'$or': [" +
            "{'busId.$id': ?0, 'scheduledStart': {'$lt': ?2}, 'scheduledEnd': {'$gt': ?1}}," +
            "{'driverId.$id': ?3, 'scheduledStart': {'$lt': ?2}, 'scheduledEnd': {'$gt': ?1}}" +
            "], 'status': {'$ne': 'CANCELLED'}}")
    List<BusOperation> findConflictingOperations(String busId, LocalDateTime start,
                                                 LocalDateTime end, String driverId);
}