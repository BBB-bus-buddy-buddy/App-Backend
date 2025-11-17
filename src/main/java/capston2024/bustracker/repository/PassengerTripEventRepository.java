package capston2024.bustracker.repository;

import capston2024.bustracker.domain.PassengerTripEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PassengerTripEventRepository extends MongoRepository<PassengerTripEvent, String> {

    List<PassengerTripEvent> findByStationIdAndTimestampBetween(String stationId, long startTimestamp, long endTimestamp);

    List<PassengerTripEvent> findByUserIdAndTimestampBetween(String userId, long startTimestamp, long endTimestamp);

    List<PassengerTripEvent> findByTimestampBetween(long startTimestamp, long endTimestamp);

    long countByOrganizationId(String organizationId);
}
