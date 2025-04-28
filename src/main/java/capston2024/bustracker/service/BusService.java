
package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.Route;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.domain.utils.BusNumberGenerator;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.RouteRepository;
import capston2024.bustracker.repository.StationRepository;
import com.mongodb.DBRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BusService {
    private static final double STATION_RADIUS = 120.0; // 120미터 반경으로 직접 설정

    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;
    private final MongoOperations mongoOperations;
    private final BusNumberGenerator busNumberGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final KakaoApiService kakaoApiService;

    // 버스 위치 업데이트 큐
    private final Map<String, BusRealTimeLocationDTO> pendingLocationUpdates = new ConcurrentHashMap<String, BusRealTimeLocationDTO>();

    @Autowired
    public BusService(
            BusRepository busRepository,
            RouteRepository routeRepository,
            StationRepository stationRepository,
            MongoOperations mongoOperations,
            BusNumberGenerator busNumberGenerator,
            ApplicationEventPublisher eventPublisher, RouteService routeService, KakaoApiService kakaoApiService) {
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.stationRepository = stationRepository;
        this.mongoOperations = mongoOperations;
        this.busNumberGenerator = busNumberGenerator;
        this.eventPublisher = eventPublisher;
        this.kakaoApiService = kakaoApiService;
    }

    /**
     * 버스 상태 업데이트 이벤트
     */
    public static class BusStatusUpdateEvent {
        private final String organizationId;
        private final BusRealTimeStatusDTO busStatus;

        public BusStatusUpdateEvent(String organizationId, BusRealTimeStatusDTO busStatus) {
            this.organizationId = organizationId;
            this.busStatus = busStatus;
        }

        public String getOrganizationId() {
            return organizationId;
        }

        public BusRealTimeStatusDTO getBusStatus() {
            return busStatus;
        }
    }

    /**
     * 버스 등록
     */
    @Transactional
    public String createBus(BusRegisterDTO busRegisterDTO, String organizationId) {
        // 라우트 존재 확인
        Route route = routeRepository.findById(busRegisterDTO.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 라우트입니다: " + busRegisterDTO.getRouteId()));

        // 요청한 조직과 라우트의 조직이 일치하는지 확인
        if (!route.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 라우트에 버스를 등록할 수 없습니다.");
        }

        // 새 버스 생성 (ID는 MongoDB가 자동 생성)
        Bus bus = Bus.builder()
                .organizationId(organizationId)
                .totalSeats(busRegisterDTO.getTotalSeats())
                .occupiedSeats(0)
                .availableSeats(busRegisterDTO.getTotalSeats())
                .location(new GeoJsonPoint(0, 0)) // 초기 위치
                .routeId(new DBRef("routes", route.getId()))
                .timestamp(Instant.now())
                .prevStationIdx(0) // 초기값은 첫 번째 정류장
                .build();

        // 저장하여 ID 획득
        bus = busRepository.save(bus);

        // 버스 ID에서 고유한 버스 번호 생성
        String busNumber = busNumberGenerator.generateBusNumber(bus.getId(), organizationId);

        // 해당 조직의 모든 버스 번호 조회
        List<String> existingBusNumbers = getAllBusesByOrganizationId(organizationId)
                .stream()
                .map(Bus::getBusNumber)
                .collect(Collectors.toList());

        // 번호가 중복되는 경우 재생성 (최대 10회 시도)
        int attempts = 0;
        while (!busNumberGenerator.isUniqueInOrganization(busNumber, existingBusNumbers) && attempts < 10) {
            busNumber = busNumberGenerator.generateBusNumber(bus.getId() + attempts, organizationId);
            attempts++;
        }

        if (!busNumberGenerator.isUniqueInOrganization(busNumber, existingBusNumbers)) {
            throw new BusinessException("고유한 버스 번호를 생성할 수 없습니다. 나중에 다시 시도해 주세요.");
        }

        // 버스 번호 업데이트
        bus.setBusNumber(busNumber);
        busRepository.save(bus);

        log.info("새로운 버스가 등록되었습니다: ID={}, 번호={}, 조직={}", bus.getId(), busNumber, organizationId);

        // 버스 등록 후 상태 업데이트 이벤트 발생
        broadcastBusStatusUpdate(bus);

        return busNumber;
    }

    /**
     * 버스 삭제
     */
    @Transactional
    public boolean removeBus(String busNumber, String organizationId) {
        Bus bus = getBusByNumberAndOrganization(busNumber, organizationId);
        busRepository.delete(bus);
        log.info("버스가 삭제되었습니다: 번호={}, 조직={}", busNumber, organizationId);
        return true;
    }

    /**
     * 버스 수정
     */
    @Transactional
    public boolean modifyBus(BusInfoUpdateDTO busInfoUpdateDTO, String organizationId) {
        if (busInfoUpdateDTO.getTotalSeats() < 0) {
            throw new IllegalArgumentException("전체 좌석 수는 0보다 작을 수 없습니다.");
        }

        // 버스 존재 확인
        Bus bus = getBusByNumberAndOrganization(busInfoUpdateDTO.getBusNumber(), organizationId);

        // 라우트 변경이 있는 경우
        if (busInfoUpdateDTO.getRouteId() != null && !busInfoUpdateDTO.getRouteId().equals(bus.getRouteId().getId().toString())) {
            Route route = routeRepository.findById(busInfoUpdateDTO.getRouteId())
                    .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 라우트입니다: " + busInfoUpdateDTO.getRouteId()));

            // 같은 조직의 라우트인지 확인
            if (!route.getOrganizationId().equals(organizationId)) {
                throw new BusinessException("다른 조직의 라우트로 변경할 수 없습니다.");
            }

            bus.setRouteId(new DBRef("routes", route.getId()));

            // 라우트 변경 시 정류장 인덱스 초기화
            bus.setPrevStationIdx(0);
            bus.setPrevStationId(null);
            bus.setLastStationTime(null);
        }

        // 좌석 정보 업데이트
        bus.setTotalSeats(busInfoUpdateDTO.getTotalSeats());
        int occupiedSeats = bus.getOccupiedSeats();

        if (occupiedSeats > busInfoUpdateDTO.getTotalSeats()) {
            log.warn("전체 좌석 수({})가 현재 사용 중인 좌석 수({})보다 적으므로 자동 조정됩니다.",
                    busInfoUpdateDTO.getTotalSeats(), occupiedSeats);
            occupiedSeats = busInfoUpdateDTO.getTotalSeats();
            bus.setOccupiedSeats(occupiedSeats);
        }

        bus.setAvailableSeats(busInfoUpdateDTO.getTotalSeats() - occupiedSeats);

        busRepository.save(bus);

        // 변경사항을 클라이언트에게 브로드캐스트
        broadcastBusStatusUpdate(bus);

        log.info("버스가 수정되었습니다: 번호={}, 조직={}", bus.getBusNumber(), organizationId);
        return true;
    }

    /**
     * 버스 ID로 특정 버스 조회
     */
    public Bus getBusById(String id) {
        return busRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + id));
    }

    /**
     * 버스 번호와 조직으로 특정 버스 조회
     */
    public Bus getBusByNumberAndOrganization(String busNumber, String organizationId) {
        return busRepository.findByBusNumberAndOrganizationId(busNumber, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("버스를 찾을 수 없습니다: 번호=%s, 조직=%s", busNumber, organizationId)));
    }

    /**
     * 조직 ID로 모든 버스 조회
     */
    public List<Bus> getAllBusesByOrganizationId(String organizationId) {
        return busRepository.findByOrganizationId(organizationId);
    }

    /**
     * 버스의 모든 정류장 상세 정보를 한 번에 조회합니다.
     * 각 정류장의 상태(지나친 정류장, 현재 정류장)와 도착 예정 시간을 포함합니다.
     *
     * @param busNumber 버스 번호
     * @param organizationId 조직 ID
     * @return 상세 정보가 포함된 정류장 목록
     */
    public List<Station> getBusStationsDetail(String busNumber, String organizationId) {
        log.info("버스 정류장 상세 정보 조회 - 버스 번호: {}, 조직: {}", busNumber, organizationId);

        // 버스 조회
        Bus bus = getBusByNumberAndOrganization(busNumber, organizationId);

        // 버스의 라우트 ID 확인
        if (bus.getRouteId() == null) {
            throw new BusinessException("버스에 할당된 라우트가 없습니다.");
        }

        String routeId = bus.getRouteId().getId().toString();

        // 라우트 조회
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 ID의 라우트를 찾을 수 없습니다: " + routeId));

        // 조직 ID 확인
        if (!route.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 라우트 정보에 접근할 수 없습니다.");
        }

        // 모든 정류장 ID 추출
        List<String> stationIds = route.getStations().stream()
                .map(routeStation -> routeStation.getStationId().getId().toString())
                .collect(Collectors.toList());

        // 한 번에 모든 정류장 조회
        Map<String, Station> stationMap = stationRepository.findAllByIdIn(stationIds).stream()
                .collect(Collectors.toMap(Station::getId, station -> station));

        // 결과 목록 및 현재 정류장 ID 준비
        List<Station> resultStations = new ArrayList<>();
        String currentStationId = null;

        // 순서대로 정류장 처리
        for (int i = 0; i < route.getStations().size(); i++) {
            Route.RouteStation routeStation = route.getStations().get(i);
            String stationId = routeStation.getStationId().getId().toString();

            Station station = stationMap.get(stationId);
            if (station == null) {
                log.warn("정류장 ID {} 정보를 찾을 수 없습니다", stationId);
                continue;
            }

            // 정류장 순서와 상태 설정
            station.setSequence(i);
            station.setPassed(i <= bus.getPrevStationIdx());
            station.setCurrentStation(i == bus.getPrevStationIdx() + 1);

            // 현재 정류장인 경우 ID 저장
            if (station.isCurrentStation()) {
                currentStationId = stationId;
            }

            resultStations.add(station);
        }

        // 현재 정류장이 있으면 도착 예정 시간 추가
        if (currentStationId != null) {
            try {
                BusArrivalEstimateResponseDTO arrivalTime =
                        kakaoApiService.getMultiWaysTimeEstimate(bus.getBusNumber(), currentStationId);

                // 현재 정류장 찾아서 도착 시간 설정
                resultStations.stream()
                        .filter(Station::isCurrentStation)
                        .findFirst()
                        .ifPresent(station -> station.setEstimatedArrivalTime(arrivalTime.getEstimatedTime()));
            } catch (Exception e) {
                log.warn("도착 시간 예측 실패: {}", e.getMessage());
            }
        }
        log.info("최종 정류장 결과 {}", resultStations);
        return resultStations;
    }

    /**
     * 버스 위치 업데이트
     */
    public void updateBusLocation(BusRealTimeLocationDTO locationUpdate) {
        log.debug("버스 위치 업데이트 요청: {}, 좌표: ({}, {})",
                locationUpdate.getBusNumber(), locationUpdate.getLatitude(), locationUpdate.getLongitude());

        // 업데이트 큐에 추가
        pendingLocationUpdates.put(locationUpdate.getBusNumber(), locationUpdate);
    }

    /**
     * 승객 탑승/하차 처리
     */
    @Transactional
    public boolean processBusBoarding(BusBoardingDTO boardingDTO) {
        log.info("승객 탑승/하차 처리: 버스={}, 사용자={}, 액션={}",
                boardingDTO.getBusNumber(), boardingDTO.getUserId(), boardingDTO.getAction());

        Bus bus = getBusByNumberAndOrganization(boardingDTO.getBusNumber(), boardingDTO.getOrganizationId());

        if (boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD) {
            // 탑승 처리
            if (bus.getOccupiedSeats() >= bus.getTotalSeats()) {
                log.warn("버스 {} 탑승 실패: 좌석이 모두 찼습니다", boardingDTO.getBusNumber());
                return false;
            }

            bus.setOccupiedSeats(bus.getOccupiedSeats() + 1);
            bus.setAvailableSeats(bus.getTotalSeats() - bus.getOccupiedSeats());

        } else {
            // 하차 처리
            if (bus.getOccupiedSeats() <= 0) {
                log.warn("버스 {} 하차 실패: 이미 버스에 탑승한 승객이 없습니다", boardingDTO.getBusNumber());
                return false;
            }

            bus.setOccupiedSeats(bus.getOccupiedSeats() - 1);
            bus.setAvailableSeats(bus.getTotalSeats() - bus.getOccupiedSeats());
        }

        busRepository.save(bus);

        // 변경사항을 클라이언트에게 브로드캐스트
        broadcastBusStatusUpdate(bus);

        return true;
    }

    /**
     * 조직별 모든 버스 상태 조회
     */
    public List<BusRealTimeStatusDTO> getAllBusStatusByOrganizationId(String organizationId) {
        List<Bus> buses = getAllBusesByOrganizationId(organizationId);
        return buses.stream()
                .map(this::convertToStatusDTO)
                .collect(Collectors.toList());
    }

    /**
     * 특정 정류장을 경유하는 조직의 모든 버스 조회
     */
    public List<BusRealTimeStatusDTO> getBusesByStationAndOrganization(String stationId, String organizationId) {
        log.info("특정 정류장을 경유하는 버스 조회 - 정류장 ID: {}, 조직 ID: {}", stationId, organizationId);

        // 조직의 모든 버스 조회
        List<Bus> organizationBuses = getAllBusesByOrganizationId(organizationId);
        List<BusRealTimeStatusDTO> result = new ArrayList<>();

        // 각 버스에 대해 라우트를 검사하여 해당 정류장을 경유하는지 확인
        for (Bus bus : organizationBuses) {
            if (bus.getRouteId() == null) continue;

            String routeId = bus.getRouteId().getId().toString();
            Route route = routeRepository.findById(routeId).orElse(null);

            if (route != null && route.getStations() != null) {
                // 라우트의 모든 정류장을 확인
                boolean containsStation = route.getStations().stream()
                        .anyMatch(routeStation -> {
                            String stationRefId = routeStation.getStationId().getId().toString();
                            return stationRefId.equals(stationId);
                        });

                // 해당 정류장을 경유하는 경우 결과에 추가
                if (containsStation) {
                    result.add(convertToStatusDTO(bus));
                }
            }
        }

        log.info("정류장 {} 경유 버스 {} 대 조회됨", stationId, result.size());
        return result;
    }

    /**
     * 버스 객체를 StatusDTO로 변환
     */
    private BusRealTimeStatusDTO convertToStatusDTO(Bus bus) {
        // 라우트 정보 조회
        Route route = null;
        if (bus.getRouteId() != null) {
            try {
                route = routeRepository.findById(bus.getRouteId().getId().toString()).orElse(null);
            } catch (Exception e) {
                log.error("라우트 정보 조회 중 오류 발생: {}", bus.getRouteId().getId(), e);
            }
        }

        String routeName = (route != null) ? route.getRouteName() : "알 수 없음";
        int totalStations = (route != null && route.getStations() != null) ? route.getStations().size() : 0;

        // 현재/마지막 정류장 정보 조회
        String currentStationName = "알 수 없음";
        if (bus.getPrevStationId() != null) {
            try {
                Station station = stationRepository.findById(bus.getPrevStationId()).orElse(null);
                if (station != null) {
                    currentStationName = station.getName();
                }
            } catch (Exception e) {
                log.error("정류장 정보 조회 중 오류 발생: {}", bus.getPrevStationId(), e);
            }
        }

        // 상태 DTO 생성
        BusRealTimeStatusDTO statusDTO = new BusRealTimeStatusDTO();
        statusDTO.setBusNumber(bus.getBusNumber());
        statusDTO.setRouteName(routeName);
        statusDTO.setOrganizationId(bus.getOrganizationId());
        statusDTO.setLatitude(bus.getLocation() != null ? bus.getLocation().getY() : 0);
        statusDTO.setLongitude(bus.getLocation() != null ? bus.getLocation().getX() : 0);
        statusDTO.setTotalSeats(bus.getTotalSeats());
        statusDTO.setOccupiedSeats(bus.getOccupiedSeats());
        statusDTO.setAvailableSeats(bus.getAvailableSeats());
        statusDTO.setCurrentStationName(currentStationName);
        statusDTO.setLastUpdateTime(bus.getTimestamp() != null ? bus.getTimestamp().toEpochMilli() : System.currentTimeMillis());
        statusDTO.setCurrentStationIndex(bus.getPrevStationIdx());
        statusDTO.setTotalStations(totalStations);

        return statusDTO;
    }

    /**
     * 버스 상태 업데이트를 클라이언트에게 브로드캐스트
     */
    private void broadcastBusStatusUpdate(Bus bus) {
        BusRealTimeStatusDTO statusDTO = convertToStatusDTO(bus);
        eventPublisher.publishEvent(new BusStatusUpdateEvent(bus.getOrganizationId(), statusDTO));
    }

    /**
     * 버스 위치 정보 얻기
     */
    public LocationDTO getBusLocationByBusNumber(String busNumber, String organizationId) {
        Bus bus = getBusByNumberAndOrganization(busNumber, organizationId);

        LocationDTO locationDTO = new LocationDTO();
        if (bus.getLocation() != null) {
            locationDTO.setLatitude(bus.getLocation().getY());
            locationDTO.setLongitude(bus.getLocation().getX());
        } else {
            locationDTO.setLatitude(0);
            locationDTO.setLongitude(0);
        }
        locationDTO.setTimestamp(bus.getTimestamp());

        return locationDTO;
    }

    /**
     * 버스 좌석 정보 얻기
     */
    public BusSeatDTO getBusSeatsByBusNumber(String busNumber, String organizationId) {
        Bus bus = getBusByNumberAndOrganization(busNumber, organizationId);

        BusSeatDTO busSeatDTO = new BusSeatDTO();
        busSeatDTO.setBusNumber(bus.getBusNumber());
        busSeatDTO.setAvailableSeats(bus.getAvailableSeats());
        busSeatDTO.setOccupiedSeats(bus.getOccupiedSeats());
        busSeatDTO.setTotalSeats(bus.getTotalSeats());

        return busSeatDTO;
    }

    /**
     * 정기적으로 버스 위치 업데이트 적용 (10초마다)
     */
    @Scheduled(fixedRate = 10000)
    public void flushLocationUpdates() {
        List<BusRealTimeLocationDTO> updates;

        synchronized (pendingLocationUpdates) {
            if (pendingLocationUpdates.isEmpty()) {
                return;
            }

            updates = new ArrayList<>(pendingLocationUpdates.values());
            pendingLocationUpdates.clear();
        }

        for (BusRealTimeLocationDTO update : updates) {
            try {
                Query query = new Query(Criteria.where("busNumber").is(update.getBusNumber())
                        .and("organizationId").is(update.getOrganizationId()));

                Bus existingBus = mongoOperations.findOne(query, Bus.class);

                if (existingBus == null) {
                    log.warn("위치 업데이트 실패: 버스를 찾을 수 없음: {}, 조직: {}",
                            update.getBusNumber(), update.getOrganizationId());
                    continue;
                }

                // 위치 및 좌석 업데이트
                GeoJsonPoint newLocation = new GeoJsonPoint(update.getLongitude(), update.getLatitude());
                Instant timestamp = Instant.ofEpochMilli(update.getTimestamp());

                // 현재 위치와 가장 가까운 정류장 찾기
                Route.RouteStation nearestStation = findNearestStation(existingBus, newLocation);

                Update mongoUpdate = new Update()
                        .set("location", newLocation)
                        .set("timestamp", timestamp)
                        .set("occupiedSeats", update.getOccupiedSeats())
                        .set("availableSeats", existingBus.getTotalSeats() - update.getOccupiedSeats());

                // 가까운 정류장이 있고, 이전 정류장과 다른 경우에만 업데이트
                if (nearestStation != null &&
                        (existingBus.getPrevStationId() == null ||
                                !existingBus.getPrevStationId().equals(nearestStation.getStationId().getId().toString()))) {

                    mongoUpdate.set("prevStationId", nearestStation.getStationId().getId().toString())
                            .set("lastStationTime", timestamp)
                            .set("prevStationIdx", nearestStation.getSequence());

                    log.info("버스 {} 정류장 업데이트: 시퀀스={}", update.getBusNumber(), nearestStation.getSequence());
                }

                mongoOperations.updateFirst(query, mongoUpdate, Bus.class);

                // 업데이트 후 버스 정보 조회
                Bus updatedBus = mongoOperations.findOne(query, Bus.class);

                // 클라이언트에게 상태 업데이트 브로드캐스트
                if (updatedBus != null) {
                    broadcastBusStatusUpdate(updatedBus);
                }

            } catch (Exception e) {
                log.error("버스 {} 위치 업데이트 중 오류 발생", update.getBusNumber(), e);
            }
        }

        if (!updates.isEmpty()) {
            log.info("{} 개의 버스 위치 정보가 업데이트되었습니다.", updates.size());
        }
    }

    /**
     * 현재 위치에서 가장 가까운 정류장 찾기
     */
    private Route.RouteStation findNearestStation(Bus bus, GeoJsonPoint location) {
        if (bus.getRouteId() == null) {
            return null;
        }

        // 라우트 정보 조회
        Route route = routeRepository.findById(bus.getRouteId().getId().toString()).orElse(null);
        if (route == null || route.getStations() == null || route.getStations().isEmpty()) {
            return null;
        }

        Route.RouteStation nearestStation = null;
        double minDistance = STATION_RADIUS;

        // 현재 인덱스 기준 주변 정류장 탐색 (전체 노선 탐색보다 효율적)
        int startIdx = Math.max(0, bus.getPrevStationIdx() - 1);
        int endIdx = Math.min(route.getStations().size(), bus.getPrevStationIdx() + 3);

        for (int i = startIdx; i < endIdx; i++) {
            if (i >= route.getStations().size()) break;

            Route.RouteStation routeStation = route.getStations().get(i);
            try {
                String stationId = routeStation.getStationId().getId().toString();
                Station station = stationRepository.findById(stationId).orElse(null);

                if (station != null && station.getLocation() != null) {
                    double distance = calculateDistance(
                            location.getY(), location.getX(),
                            station.getLocation().getY(), station.getLocation().getX()
                    );

                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestStation = routeStation;
                    }
                }
            } catch (Exception e) {
                log.error("정류장 정보 조회 중 오류 발생: {}", routeStation.getStationId(), e);
            }
        }

        return nearestStation;
    }
    /**
     * 두 위치 사이의 거리 계산 (Haversine 공식)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // 지구의 반지름 (미터)

        // 위도, 경도를 라디안으로 변환
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        // 위도, 경도 차이
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;

        // Haversine 공식
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        // 최종 거리 (미터 단위)
        return R * c;
    }
}