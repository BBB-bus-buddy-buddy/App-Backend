package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.busEtc.DriverLocationUpdateDTO;
import capston2024.bustracker.config.dto.busEtc.SeatInfoDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.domain.Route;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusOperationRepository;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeatInfoService {

    private final BusRepository busRepository;
    private final BusOperationRepository busOperationRepository;
    private final RouteRepository routeRepository;
    private final RealtimeLocationService realtimeLocationService;

    /**
     * 특정 버스 번호의 현재 좌석 정보 조회
     */
    public SeatInfoDTO getBusSeatInfo(String busNumber, String organizationId) {
        log.info("버스 {} 좌석 정보 조회 - 조직: {}", busNumber, organizationId);

        // 버스 조회
        Bus bus = busRepository.findByBusNumberAndOrganizationId(busNumber, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busNumber));

        // 현재 진행 중인 운행 찾기
        List<BusOperation> activeOperations = busOperationRepository.findByBusIdAndStatusIn(
                bus.getId(),
                List.of(BusOperation.OperationStatus.IN_PROGRESS, BusOperation.OperationStatus.SCHEDULED)
        );

        if (activeOperations.isEmpty()) {
            // 운행 중이 아닌 경우 기본 정보만 반환
            String routeName = null;
            if (bus.getRouteId() != null) {
                Route route = routeRepository.findById(bus.getRouteId().getId().toString()).orElse(null);
                if (route != null) {
                    routeName = route.getRouteName();
                }
            }

            return SeatInfoDTO.builder()
                    .operationId(null)
                    .busNumber(busNumber)
                    .busRealNumber(bus.getBusRealNumber())
                    .routeName(routeName)
                    .totalSeats(bus.getTotalSeats())
                    .currentPassengers(0)
                    .availableSeats(bus.getTotalSeats())
                    .occupancyRate(0.0)
                    .lastUpdated(System.currentTimeMillis())
                    .isOperating(false)
                    .build();
        }

        // 진행 중인 운행 우선
        BusOperation currentOperation = activeOperations.stream()
                .filter(op -> op.getStatus() == BusOperation.OperationStatus.IN_PROGRESS)
                .findFirst()
                .orElse(activeOperations.get(0));

        // 상세 정보가 포함된 SeatInfoDTO 생성
        return createSeatInfoFromOperation(currentOperation);
    }

    /**
     * 조직의 모든 운행 중인 버스 좌석 정보 조회
     */
    public List<SeatInfoDTO> getActiveOperationsSeatInfo(String organizationId) {
        log.info("조직 {} 운행 중인 버스 좌석 정보 조회", organizationId);

        // 진행 중인 운행 조회
        List<BusOperation> activeOperations = busOperationRepository.findByOrganizationIdAndStatus(
                organizationId, BusOperation.OperationStatus.IN_PROGRESS);

        List<SeatInfoDTO> seatInfoList = new ArrayList<>();

        for (BusOperation operation : activeOperations) {
            try {
                SeatInfoDTO seatInfo = createSeatInfoFromOperation(operation);
                if (seatInfo != null) {
                    seatInfoList.add(seatInfo);
                }
            } catch (Exception e) {
                log.error("운행 {} 좌석 정보 생성 중 오류", operation.getOperationId(), e);
            }
        }

        return seatInfoList;
    }

    /**
     * 특정 노선의 모든 운행 중인 버스 좌석 정보 조회
     */
    public List<SeatInfoDTO> getRouteSeatInfo(String routeId, String organizationId) {
        log.info("노선 {} 버스 좌석 정보 조회 - 조직: {}", routeId, organizationId);

        // 노선 존재 및 조직 확인
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("노선을 찾을 수 없습니다: " + routeId));

        if (!route.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 노선 정보를 조회할 수 없습니다.");
        }

        // 해당 노선에 할당된 버스들 조회
        List<Bus> routeBuses = busRepository.findByRouteId(routeId);
        List<SeatInfoDTO> seatInfoList = new ArrayList<>();

        for (Bus bus : routeBuses) {
            try {
                // 각 버스의 현재 운행 찾기
                List<BusOperation> operations = busOperationRepository.findByBusIdAndStatusIn(
                        bus.getId(),
                        List.of(BusOperation.OperationStatus.IN_PROGRESS)
                );

                if (!operations.isEmpty()) {
                    BusOperation operation = operations.get(0);
                    SeatInfoDTO seatInfo = createSeatInfoFromOperation(operation);
                    if (seatInfo != null) {
                        seatInfoList.add(seatInfo);
                    }
                } else {
                    // 운행 중이 아니어도 버스 정보는 표시
                    seatInfoList.add(new SeatInfoDTO(
                            null,
                            bus.getBusNumber(),
                            bus.getTotalSeats(),
                            0,
                            bus.getTotalSeats(),
                            System.currentTimeMillis()
                    ));
                }
            } catch (Exception e) {
                log.error("버스 {} 좌석 정보 생성 중 오류", bus.getBusNumber(), e);
            }
        }

        return seatInfoList;
    }

    /**
     * 좌석 가용성 기준 버스 조회
     */
    public List<SeatInfoDTO> getAvailableBuses(String organizationId, int minSeats, String routeId) {
        log.info("최소 {}석 이상 가용한 버스 조회 - 조직: {}, 노선: {}", minSeats, organizationId, routeId);

        List<SeatInfoDTO> allSeatInfo;

        if (routeId != null && !routeId.isEmpty()) {
            // 특정 노선의 버스들만 조회
            allSeatInfo = getRouteSeatInfo(routeId, organizationId);
        } else {
            // 조직의 모든 운행 중인 버스 조회
            allSeatInfo = getActiveOperationsSeatInfo(organizationId);
        }

        // 최소 좌석 수 이상인 버스만 필터링
        return allSeatInfo.stream()
                .filter(seatInfo -> seatInfo.getAvailableSeats() >= minSeats)
                .sorted((a, b) -> Integer.compare(b.getAvailableSeats(), a.getAvailableSeats())) // 가용 좌석 많은 순
                .collect(Collectors.toList());
    }

    /**
     * BusOperation에서 SeatInfoDTO 생성
     */
    private SeatInfoDTO createSeatInfoFromOperation(BusOperation operation) {
        try {
            // 버스 정보 조회
            if (operation.getBusId() == null) {
                return null;
            }

            Bus bus = busRepository.findById(operation.getBusId().getId().toString())
                    .orElse(null);

            if (bus == null) {
                return null;
            }

            // 현재 승객 수 가져오기
            int currentPassengers = getCurrentPassengers(operation);

            // 실시간 위치 정보
            DriverLocationUpdateDTO realtimeLocation = realtimeLocationService
                    .getCurrentLocation(operation.getOperationId());

            // 노선 정보 조회
            String routeName = null;
            if (operation.getRouteId() != null) {
                Route route = routeRepository.findById(operation.getRouteId().getId().toString())
                        .orElse(null);
                if (route != null) {
                    routeName = route.getRouteName();
                }
            }

            // 기사 정보 (필요시 UserRepository 통해 조회)
            String driverName = "기사님"; // 기본값

            return SeatInfoDTO.builder()
                    .operationId(operation.getOperationId())
                    .busNumber(bus.getBusNumber())
                    .busRealNumber(bus.getBusRealNumber())
                    .routeName(routeName)
                    .totalSeats(bus.getTotalSeats())
                    .currentPassengers(currentPassengers)
                    .availableSeats(bus.getTotalSeats() - currentPassengers)
                    .occupancyRate((currentPassengers * 100.0) / bus.getTotalSeats())
                    .driverName(driverName)
                    .latitude(realtimeLocation != null ? realtimeLocation.getLatitude() : null)
                    .longitude(realtimeLocation != null ? realtimeLocation.getLongitude() : null)
                    .lastUpdated(operation.getUpdatedAt() != null ?
                            operation.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                            System.currentTimeMillis())
                    .isOperating(operation.getStatus() == BusOperation.OperationStatus.IN_PROGRESS)
                    .build();

        } catch (Exception e) {
            log.error("SeatInfoDTO 생성 중 오류: 운행={}", operation.getOperationId(), e);
            return null;
        }
    }

    /**
     * 현재 승객 수 조회 (실시간 정보 우선, 없으면 DB 정보)
     */
    private int getCurrentPassengers(BusOperation operation) {
        // 실시간 위치 정보에서 승객 수 확인
        DriverLocationUpdateDTO realtimeLocation = realtimeLocationService
                .getCurrentLocation(operation.getOperationId());

        if (realtimeLocation != null) {
            return realtimeLocation.getCurrentPassengers();
        }

        // 실시간 정보가 없으면 DB의 정보 사용
        return operation.getTotalPassengers() != null ? operation.getTotalPassengers() : 0;
    }

    /**
     * 특정 정류장에서 탑승 가능한 버스 조회
     */
    public List<SeatInfoDTO> getAvailableBusesAtStation(String stationId, String organizationId, int requiredSeats) {
        log.info("정류장 {}에서 {}석 이상 탑승 가능한 버스 조회", stationId, requiredSeats);

        // 해당 정류장을 경유하는 노선들 찾기
        List<Route> routes = routeRepository.findByStationId(stationId);

        List<SeatInfoDTO> availableBuses = new ArrayList<>();

        for (Route route : routes) {
            if (!route.getOrganizationId().equals(organizationId)) {
                continue;
            }

            // 해당 노선의 버스들 중 가용 좌석이 있는 버스 찾기
            List<SeatInfoDTO> routeBuses = getAvailableBuses(organizationId, requiredSeats, route.getId());
            availableBuses.addAll(routeBuses);
        }

        return availableBuses;
    }
}