package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.busEtc.DriverLocationUpdateDTO;
import capston2024.bustracker.config.dto.busEtc.PassengerBusStatusDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationDTO;
import capston2024.bustracker.config.dto.realtime.BusRealtimeStatusDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.domain.BusTrackingEvent;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusOperationRepository;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.BusTrackingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RealtimeLocationService {

    private final BusOperationRepository busOperationRepository;
    private final BusOperationService busOperationService;
    private final BusRepository busRepository;
    private final BusTrackingEventRepository busTrackingEventRepository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;

    // 실시간 위치 캐시 (operationId -> 위치 정보)
    private final Map<String, DriverLocationUpdateDTO> locationCache = new ConcurrentHashMap<>();

    /**
     * 기사 앱에서 전송한 실시간 위치 업데이트 처리
     */
    @Transactional
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

            // 2. 버스 엔티티의 위치 정보 업데이트
            updateBusLocation(operation.getBusId().getId().toString(),
                    locationUpdate.getLatitude(),
                    locationUpdate.getLongitude());

            // 3. 실시간 위치 캐시 업데이트
            locationCache.put(locationUpdate.getOperationId(), locationUpdate);

            // 4. BusOperation의 승객 수 업데이트
            Query query = new Query(Criteria.where("operationId").is(locationUpdate.getOperationId()));
            Update update = new Update()
                    .set("totalPassengers", locationUpdate.getCurrentPassengers())
                    .set("updatedAt", LocalDateTime.now());

            mongoTemplate.updateFirst(query, update, BusOperation.class);

            // 5. BusTrackingEvent 저장
            saveBusTrackingEvent(operation, locationUpdate, "LOCATION_UPDATE");

            // 6. 승객 앱에 실시간 상태 브로드캐스트
            broadcastToPassengers(operation, locationUpdate);

            return true;

        } catch (Exception e) {
            log.error("기사 위치 업데이트 처리 중 오류", e);
            return false;
        }
    }

    /**
     * 버스 엔티티의 위치 정보 업데이트
     */
    private void updateBusLocation(String busId, double latitude, double longitude) {
        Query query = new Query(Criteria.where("id").is(busId));
        Update update = new Update()
                .set("location", new GeoJsonPoint(longitude, latitude))
                .set("lastLocationUpdate", Instant.now());

        mongoTemplate.updateFirst(query, update, Bus.class);
        log.debug("버스 {} 위치 업데이트 완료", busId);
    }

    /**
     * BusTrackingEvent 저장
     */
    private void saveBusTrackingEvent(BusOperation operation, DriverLocationUpdateDTO locationUpdate, String eventType) {
        try {
            // 버스 정보 조회
            Bus bus = busRepository.findById(operation.getBusId().getId().toString())
                    .orElseThrow(() -> new ResourceNotFoundException("버스 정보를 찾을 수 없습니다"));

            // 이벤트 메타데이터 구성
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("busNumber", bus.getBusNumber());
            metadata.put("routeId", bus.getRouteId() != null ? bus.getRouteId().getId().toString() : null);
            metadata.put("driverId", operation.getDriverId() != null ? operation.getDriverId().getId().toString() : null);

            // 좌석 상태 정보
            BusTrackingEvent.SeatStatus seatStatus = new BusTrackingEvent.SeatStatus(
                    locationUpdate.getCurrentPassengers(),
                    bus.getTotalSeats() - locationUpdate.getCurrentPassengers(),
                    bus.getTotalSeats()
            );

            // 이벤트 생성 및 저장
            BusTrackingEvent event = BusTrackingEvent.builder()
                    .timestamp(Instant.now())
                    .eventType(eventType)
                    .busId(bus.getId())
                    .operationId(operation.getOperationId())
                    .organizationId(operation.getOrganizationId())
                    .location(new GeoJsonPoint(locationUpdate.getLongitude(), locationUpdate.getLatitude()))
                    .seatStatus(seatStatus)
                    .metadata(metadata)
                    .build();

            busTrackingEventRepository.save(event);
            log.debug("BusTrackingEvent 저장 완료: 타입={}, 운행={}", eventType, operation.getOperationId());

        } catch (Exception e) {
            log.error("BusTrackingEvent 저장 중 오류", e);
        }
    }

    /**
     * 탑승/하차 이벤트 저장
     */
    public void saveBoardingEvent(String operationId, String userId, String eventType, double latitude, double longitude) {
        try {
            BusOperation operation = busOperationRepository.findByOperationId(operationId)
                    .orElseThrow(() -> new ResourceNotFoundException("운행을 찾을 수 없습니다: " + operationId));

            Bus bus = busRepository.findById(operation.getBusId().getId().toString())
                    .orElseThrow(() -> new ResourceNotFoundException("버스 정보를 찾을 수 없습니다"));

            // 좌석 상태 정보
            int currentPassengers = operation.getTotalPassengers() != null ? operation.getTotalPassengers() : 0;
            BusTrackingEvent.SeatStatus seatStatus = new BusTrackingEvent.SeatStatus(
                    currentPassengers,
                    bus.getTotalSeats() - currentPassengers,
                    bus.getTotalSeats()
            );

            // 이벤트 메타데이터
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("busNumber", bus.getBusNumber());
            metadata.put("userId", userId);
            metadata.put("action", eventType);

            // 이벤트 생성 및 저장
            BusTrackingEvent event = BusTrackingEvent.builder()
                    .timestamp(Instant.now())
                    .eventType(eventType)
                    .busId(bus.getId())
                    .operationId(operationId)
                    .organizationId(operation.getOrganizationId())
                    .userId(userId)
                    .location(new GeoJsonPoint(longitude, latitude))
                    .seatStatus(seatStatus)
                    .metadata(metadata)
                    .build();

            busTrackingEventRepository.save(event);
            log.info("탑승/하차 이벤트 저장: 사용자={}, 타입={}, 운행={}", userId, eventType, operationId);

        } catch (Exception e) {
            log.error("탑승/하차 이벤트 저장 중 오류", e);
        }
    }

    /**
     * 운행의 현재 실시간 위치 조회
     */
    public DriverLocationUpdateDTO getCurrentLocation(String operationId) {
        return locationCache.get(operationId);
    }

    /**
     * 조직의 모든 진행 중인 버스 상태 조회
     */
    public List<BusRealtimeStatusDTO> getOrganizationBusStatuses(String organizationId) {
        // 진행 중인 운행 조회
        List<BusOperation> activeOperations = busOperationRepository.findByOrganizationIdAndStatus(
                organizationId, BusOperation.OperationStatus.IN_PROGRESS);

        return activeOperations.stream()
                .map(operation -> {
                    try {
                        DriverLocationUpdateDTO location = locationCache.get(operation.getOperationId());
                        if (location == null) {
                            return null;
                        }

                        BusOperationDTO operationDTO = busOperationService.convertToDTO(operation);
                        Bus bus = busRepository.findById(operation.getBusId().getId().toString()).orElse(null);

                        if (bus == null) {
                            return null;
                        }

                        return BusRealtimeStatusDTO.builder()
                                .operationId(operation.getOperationId())
                                .busNumber(bus.getBusNumber())
                                .busRealNumber(bus.getBusRealNumber())
                                .routeName(operationDTO.getRouteName())
                                .organizationId(organizationId)
                                .latitude(location.getLatitude())
                                .longitude(location.getLongitude())
                                .totalSeats(bus.getTotalSeats())
                                .currentPassengers(location.getCurrentPassengers())
                                .availableSeats(bus.getTotalSeats() - location.getCurrentPassengers())
                                .driverName(operationDTO.getDriverName())
                                .operationStatus(operation.getStatus())
                                .lastUpdateTime(location.getTimestamp())
                                .isCurrentlyOperating(true)
                                .build();
                    } catch (Exception e) {
                        log.error("버스 상태 조회 중 오류: 운행={}", operation.getOperationId(), e);
                        return null;
                    }
                })
                .filter(status -> status != null)
                .toList();
    }

    /**
     * 승객 앱에 실시간 버스 상태 브로드캐스트
     */
    private void broadcastToPassengers(BusOperation operation, DriverLocationUpdateDTO locationUpdate) {
        try {
            BusOperationDTO operationDTO = busOperationService.convertToDTO(operation);
            Bus bus = busRepository.findById(operation.getBusId().getId().toString())
                    .orElseThrow(() -> new ResourceNotFoundException("버스 정보를 찾을 수 없습니다"));

            BusRealtimeStatusDTO realtimeStatus = BusRealtimeStatusDTO.builder()
                    .operationId(operation.getOperationId())
                    .busNumber(bus.getBusNumber())
                    .busRealNumber(bus.getBusRealNumber())
                    .routeName(operationDTO.getRouteName())
                    .organizationId(operation.getOrganizationId())
                    .latitude(locationUpdate.getLatitude())
                    .longitude(locationUpdate.getLongitude())
                    .totalSeats(bus.getTotalSeats())
                    .currentPassengers(locationUpdate.getCurrentPassengers())
                    .availableSeats(bus.getTotalSeats() - locationUpdate.getCurrentPassengers())
                    .driverName(operationDTO.getDriverName())
                    .operationStatus(operation.getStatus())
                    .lastUpdateTime(locationUpdate.getTimestamp())
                    .isCurrentlyOperating(true)
                    .build();

            // 이벤트 발행
            eventPublisher.publishEvent(new PassengerBroadcastEvent(operation.getOrganizationId(), realtimeStatus));

        } catch (Exception e) {
            log.error("승객 앱 브로드캐스트 중 오류", e);
        }
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
     * 만료된 위치 정보 정리 (5분마다 실행)
     */
    @Scheduled(fixedDelay = 300000) // 5분
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
    public record PassengerBroadcastEvent(String organizationId, BusRealtimeStatusDTO busStatus) {}
}