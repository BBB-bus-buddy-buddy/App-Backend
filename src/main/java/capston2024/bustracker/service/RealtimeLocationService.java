package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.busEtc.DriverLocationUpdateDTO;
import capston2024.bustracker.config.dto.busEtc.PassengerBusStatusDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusOperationRepository;
import capston2024.bustracker.repository.BusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RealtimeLocationService {

    private final BusOperationRepository busOperationRepository;
    private final BusOperationService busOperationService;
    private final BusRepository busRepository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;

    // 실시간 위치 캐시 (operationId -> 위치 정보)
    private final Map<String, DriverLocationUpdateDTO> locationCache = new ConcurrentHashMap<>();

    /**
     * 기사 앱에서 전송한 실시간 위치 업데이트 처리
     */
    public boolean updateDriverLocation(DriverLocationUpdateDTO locationUpdate) {
        try {
            log.debug("기사 위치 업데이트: 운행ID={}, 좌표=({}, {}), 승객수={}",
                    locationUpdate.getOperationId(),
                    locationUpdate.getLatitude(),
                    locationUpdate.getLongitude(),
                    locationUpdate.getCurrentPassengers());

            // 1. 운행 존재 및 상태 확인
            BusOperation operation = busOperationRepository.findByOperationId(locationUpdate.getOperationId())
                    .orElseThrow(() -> new ResourceNotFoundException("운행을 찾을 수 없습니다: " + locationUpdate.getOperationId()));

            if (operation.getStatus() != BusOperation.OperationStatus.IN_PROGRESS) {
                log.warn("진행 중이 아닌 운행의 위치 업데이트: {}", locationUpdate.getOperationId());
                return false;
            }

            // 2. 실시간 위치 캐시 업데이트
            locationCache.put(locationUpdate.getOperationId(), locationUpdate);

            // 3. BusOperation의 승객 수 업데이트
            Query query = new Query(Criteria.where("operationId").is(locationUpdate.getOperationId()));
            Update update = new Update()
                    .set("totalPassengers", locationUpdate.getCurrentPassengers())
                    .set("updatedAt", LocalDateTime.now());

            mongoTemplate.updateFirst(query, update, BusOperation.class);

            // 4. 승객 앱에 실시간 상태 브로드캐스트
            broadcastToPassengers(operation, locationUpdate);

            return true;

        } catch (Exception e) {
            log.error("기사 위치 업데이트 처리 중 오류", e);
            return false;
        }
    }

    /**
     * 운행의 현재 실시간 위치 조회
     */
    public DriverLocationUpdateDTO getCurrentLocation(String operationId) {
        return locationCache.get(operationId);
    }

    /**
     * 승객 앱에 실시간 버스 상태 브로드캐스트
     */
    private void broadcastToPassengers(BusOperation operation, DriverLocationUpdateDTO locationUpdate) {
        try {
            // BusOperation → PassengerBusStatusDTO 변환
            BusOperationDTO operationDTO = busOperationService.convertToDTO(operation);

            PassengerBusStatusDTO passengerStatus = PassengerBusStatusDTO.builder()
                    .operationId(operation.getOperationId())
                    .busNumber(operationDTO.getBusNumber())
                    .busRealNumber(operationDTO.getBusRealNumber())
                    .routeName(operationDTO.getRouteName())
                    .organizationId(operation.getOrganizationId())
                    .latitude(locationUpdate.getLatitude())
                    .longitude(locationUpdate.getLongitude())
                    .totalSeats(getTotalSeats(operation))
                    .currentPassengers(locationUpdate.getCurrentPassengers())
                    .availableSeats(getTotalSeats(operation) - locationUpdate.getCurrentPassengers())
                    .driverName(operationDTO.getDriverName())
                    .lastUpdateTime(locationUpdate.getTimestamp())
                    .isActive(operation.getStatus() == BusOperation.OperationStatus.IN_PROGRESS)
                    .build();

            // 이벤트 발행
            eventPublisher.publishEvent(new PassengerBroadcastEvent(operation.getOrganizationId(), passengerStatus));

        } catch (Exception e) {
            log.error("승객 앱 브로드캐스트 중 오류", e);
        }
    }

    /**
     * 버스 총 좌석 수 조회 (BusOperation에서 Bus 정보 추적)
     */
    private int getTotalSeats(BusOperation operation) {
        try {
            if (operation.getBusId() == null) {
                log.warn("운행 {}에 버스 정보가 없습니다", operation.getOperationId());
                return 0;
            }

            String busId = operation.getBusId().getId().toString();
            Bus bus = busRepository.findById(busId)
                    .orElseThrow(() -> new ResourceNotFoundException("버스 정보를 찾을 수 없습니다: " + busId));

            return bus.getTotalSeats();

        } catch (Exception e) {
            log.error("버스 좌석 수 조회 중 오류 발생: 운행={}", operation.getOperationId(), e);
            return 0; // 기본값 반환
        }
    }

    /**
     * BusOperation에서 버스 번호 조회
     */
    public String getBusNumberFromOperation(BusOperation operation) {
        try {
            if (operation.getBusId() == null) {
                log.warn("운행 {}에 버스 정보가 없습니다", operation.getOperationId());
                return "알 수 없음";
            }

            String busId = operation.getBusId().getId().toString();
            Bus bus = busRepository.findById(busId)
                    .orElseThrow(() -> new ResourceNotFoundException("버스 정보를 찾을 수 없습니다: " + busId));

            return bus.getBusNumber();

        } catch (Exception e) {
            log.error("버스 번호 조회 중 오류 발생: 운행={}", operation.getOperationId(), e);
            return "알 수 없음";
        }
    }

    /**
     * 조직의 모든 활성 운행의 실시간 위치 조회
     */
    public Map<String, DriverLocationUpdateDTO> getAllActiveLocations(String organizationId) {
        Map<String, DriverLocationUpdateDTO> result = new ConcurrentHashMap<>();

        locationCache.entrySet().stream()
                .filter(entry -> {
                    try {
                        BusOperation operation = busOperationRepository.findByOperationId(entry.getKey()).orElse(null);
                        return operation != null &&
                                operation.getOrganizationId().equals(organizationId) &&
                                operation.getStatus() == BusOperation.OperationStatus.IN_PROGRESS;
                    } catch (Exception e) {
                        log.warn("운행 상태 확인 중 오류: {}", entry.getKey(), e);
                        return false;
                    }
                })
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));

        return result;
    }

    /**
     * 특정 운행의 실시간 위치 제거 (운행 종료 시)
     */
    public void removeOperationLocation(String operationId) {
        DriverLocationUpdateDTO removed = locationCache.remove(operationId);
        if (removed != null) {
            log.info("운행 {} 실시간 위치 캐시 제거", operationId);
        }
    }

    /**
     * 만료된 위치 정보 정리 (스케줄러로 주기적 실행)
     */
    public void cleanupExpiredLocations() {
        long currentTime = System.currentTimeMillis();
        long expireTime = 10 * 60 * 1000; // 10분

        locationCache.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue().getTimestamp()) > expireTime;
            if (expired) {
                log.debug("만료된 위치 정보 제거: 운행={}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * 승객 앱 브로드캐스트 이벤트
     */
    public record PassengerBroadcastEvent(String organizationId, PassengerBusStatusDTO busStatus) {}
}