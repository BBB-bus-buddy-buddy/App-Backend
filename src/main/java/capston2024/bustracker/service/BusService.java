package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.bus.BusCreateDTO;
import capston2024.bustracker.config.dto.bus.BusStatusDTO;
import capston2024.bustracker.config.dto.bus.BusUpdateDTO;
import capston2024.bustracker.config.dto.busEtc.*;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.domain.Route;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.domain.utils.BusNumberGenerator;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusOperationRepository;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.RouteRepository;
import capston2024.bustracker.repository.StationRepository;
import com.mongodb.DBRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BusService {

    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;
    private final BusOperationRepository busOperationRepository;
    private final BusNumberGenerator busNumberGenerator;
    private final RealtimeLocationService realtimeLocationService;
    private final KakaoApiService kakaoApiService;

    /**
     * 버스 등록 (정적 정보만)
     */
    @Transactional
    public String createBus(BusCreateDTO busCreateDTO, String organizationId) {
        // 노선 존재 확인
        Route route = routeRepository.findById(busCreateDTO.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 노선입니다: " + busCreateDTO.getRouteId()));

        // 요청한 조직과 노선의 조직이 일치하는지 확인
        if (!route.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 노선에 버스를 등록할 수 없습니다.");
        }

        // 새 버스 생성
        Bus bus = Bus.builder()
                .organizationId(organizationId)
                .busRealNumber(busCreateDTO.getBusRealNumber() != null ?
                        busCreateDTO.getBusRealNumber().trim() : null)
                .totalSeats(busCreateDTO.getTotalSeats())
                .routeId(new DBRef("routes", route.getId()))
                .operationalStatus(busCreateDTO.getOperationalStatus())
                .serviceStatus(busCreateDTO.getServiceStatus())
                .build();

        // 저장하여 ID 획득
        bus = busRepository.save(bus);

        // 버스 번호 생성
        String busNumber = generateUniqueBusNumber(bus.getId(), organizationId);
        bus.setBusNumber(busNumber);
        busRepository.save(bus);

        log.info("새로운 버스가 등록되었습니다: ID={}, 번호={}, 조직={}, 운영상태={}, 서비스상태={}",
                bus.getId(), busNumber, organizationId, bus.getOperationalStatus(), bus.getServiceStatus());

        return busNumber;
    }

    /**
     * 버스 정보 수정 (정적 정보만)
     */
    @Transactional
    public boolean updateBus(BusUpdateDTO busUpdateDTO, String organizationId) {
        Bus bus = getBusByNumberAndOrganization(busUpdateDTO.getBusNumber(), organizationId);

        // 실제 버스 번호 수정
        if (busUpdateDTO.getBusRealNumber() != null) {
            String newRealNumber = busUpdateDTO.getBusRealNumber().trim();
            bus.setBusRealNumber(newRealNumber.isEmpty() ? null : newRealNumber);
        }

        // 운영 상태 수정
        if (busUpdateDTO.getOperationalStatus() != null) {
            bus.setOperationalStatus(busUpdateDTO.getOperationalStatus());
        }

        // 서비스 상태 수정
        if (busUpdateDTO.getServiceStatus() != null) {
            bus.setServiceStatus(busUpdateDTO.getServiceStatus());
        }

        // 노선 변경
        if (busUpdateDTO.getRouteId() != null) {
            Route route = routeRepository.findById(busUpdateDTO.getRouteId())
                    .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 노선입니다: " + busUpdateDTO.getRouteId()));

            if (!route.getOrganizationId().equals(organizationId)) {
                throw new BusinessException("다른 조직의 노선으로 변경할 수 없습니다.");
            }

            bus.setRouteId(new DBRef("routes", route.getId()));
        }

        // 좌석 수 변경
        if (busUpdateDTO.getTotalSeats() != null) {
            bus.setTotalSeats(busUpdateDTO.getTotalSeats());
        }

        busRepository.save(bus);

        log.info("버스가 수정되었습니다: 번호={}, 조직={}", bus.getBusNumber(), organizationId);
        return true;
    }

    /**
     * 조직별 모든 버스 조회 (상태 정보 포함)
     */
    public List<BusStatusDTO> getAllBusStatusByOrganizationId(String organizationId) {
        List<Bus> buses = busRepository.findByOrganizationId(organizationId);
        return buses.stream()
                .map(bus -> convertToBusStatusDTO(bus))
                .collect(Collectors.toList());
    }

    /**
     * 운영 상태별 버스 조회
     */
    public List<BusStatusDTO> getBusesByOperationalStatus(String organizationId, Bus.OperationalStatus status) {
        List<Bus> buses = busRepository.findByOrganizationIdAndOperationalStatus(organizationId, status);
        return buses.stream()
                .map(this::convertToBusStatusDTO)
                .collect(Collectors.toList());
    }

    /**
     * 서비스 상태별 버스 조회
     */
    public List<BusStatusDTO> getBusesByServiceStatus(String organizationId, Bus.ServiceStatus status) {
        List<Bus> buses = busRepository.findByOrganizationIdAndServiceStatus(organizationId, status);
        return buses.stream()
                .map(this::convertToBusStatusDTO)
                .collect(Collectors.toList());
    }

    /**
     * 현재 운행 가능한 버스 조회
     */
    public List<BusStatusDTO> getOperationalBuses(String organizationId) {
        List<Bus> buses = busRepository.findByOrganizationIdAndOperationalStatusAndServiceStatus(
                organizationId, Bus.OperationalStatus.ACTIVE, Bus.ServiceStatus.IN_SERVICE);
        return buses.stream()
                .map(this::convertToBusStatusDTO)
                .collect(Collectors.toList());
    }

    /**
     * 버스 상태 변경
     */
    @Transactional
    public boolean updateBusStatus(String busNumber, String organizationId,
                                   Bus.OperationalStatus operationalStatus,
                                   Bus.ServiceStatus serviceStatus) {
        Bus bus = getBusByNumberAndOrganization(busNumber, organizationId);

        if (operationalStatus != null) {
            bus.setOperationalStatus(operationalStatus);
        }

        if (serviceStatus != null) {
            bus.setServiceStatus(serviceStatus);
        }

        busRepository.save(bus);

        log.info("버스 상태 변경: 번호={}, 운영상태={}, 서비스상태={}",
                busNumber, operationalStatus, serviceStatus);
        return true;
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
     * 버스 삭제
     */
    @Transactional
    public boolean removeBus(String busNumber, String organizationId) {
        Bus bus = getBusByNumberAndOrganization(busNumber, organizationId);

        // 진행 중인 운행이 있는지 확인
        List<BusOperation> activeOperations = busOperationRepository.findByBusIdAndStatusIn(
                bus.getId(), List.of(BusOperation.OperationStatus.SCHEDULED, BusOperation.OperationStatus.IN_PROGRESS));

        if (!activeOperations.isEmpty()) {
            throw new BusinessException("진행 중이거나 예정된 운행이 있는 버스는 삭제할 수 없습니다.");
        }

        busRepository.delete(bus);
        log.info("버스가 삭제되었습니다: 번호={}, 조직={}", busNumber, organizationId);
        return true;
    }

    /**
     * Bus 엔티티를 BusStatusDTO로 변환
     */
    private BusStatusDTO convertToBusStatusDTO(Bus bus) {
        // 라우트 정보 조회
        String routeName = "미할당";
        if (bus.getRouteId() != null) {
            try {
                Route route = routeRepository.findById(bus.getRouteId().getId().toString()).orElse(null);
                if (route != null) {
                    routeName = route.getRouteName();
                }
            } catch (Exception e) {
                log.warn("라우트 정보 조회 실패: {}", bus.getRouteId().getId(), e);
            }
        }

        // 현재 운행 정보 조회
        BusOperation currentOperation = getCurrentOperation(bus.getId());

        Integer currentPassengers = null;
        Integer availableSeats = null;
        String currentOperationId = null;
        String currentDriverName = null;
        boolean isCurrentlyOperating = false;

        if (currentOperation != null) {
            currentPassengers = currentOperation.getTotalPassengers();
            availableSeats = bus.getTotalSeats() - (currentPassengers != null ? currentPassengers : 0);
            currentOperationId = currentOperation.getOperationId();
            isCurrentlyOperating = currentOperation.getStatus() == BusOperation.OperationStatus.IN_PROGRESS;

            // 기사 정보 조회
            if (currentOperation.getDriverId() != null) {
                // UserRepository를 통해 기사 정보 조회 (필요시 추가)
            }
        }

        return BusStatusDTO.builder()
                .busNumber(bus.getBusNumber())
                .busRealNumber(bus.getBusRealNumber())
                .routeName(routeName)
                .operationalStatus(bus.getOperationalStatus())
                .serviceStatus(bus.getServiceStatus())
                .totalSeats(bus.getTotalSeats())
                .currentPassengers(currentPassengers)
                .availableSeats(availableSeats)
                .currentOperationId(currentOperationId)
                .currentDriverName(currentDriverName)
                .isCurrentlyOperating(isCurrentlyOperating)
                .build();
    }

    /**
     * 현재 진행 중인 운행 조회
     */
    private BusOperation getCurrentOperation(String busId) {
        List<BusOperation> activeOperations = busOperationRepository.findByBusIdAndStatusIn(
                busId, List.of(BusOperation.OperationStatus.IN_PROGRESS, BusOperation.OperationStatus.SCHEDULED));

        return activeOperations.stream()
                .filter(op -> op.getStatus() == BusOperation.OperationStatus.IN_PROGRESS)
                .findFirst()
                .orElse(activeOperations.stream().findFirst().orElse(null));
    }

    /**
     * 고유한 버스 번호 생성
     */
    private String generateUniqueBusNumber(String busId, String organizationId) {
        String busNumber = busNumberGenerator.generateBusNumber(busId, organizationId);

        List<String> existingNumbers = busRepository.findByOrganizationId(organizationId)
                .stream()
                .map(Bus::getBusNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int attempts = 0;
        while (!busNumberGenerator.isUniqueInOrganization(busNumber, existingNumbers) && attempts < 10) {
            busNumber = busNumberGenerator.generateBusNumber(busId + attempts, organizationId);
            attempts++;
        }

        if (!busNumberGenerator.isUniqueInOrganization(busNumber, existingNumbers)) {
            throw new BusinessException("고유한 버스 번호를 생성할 수 없습니다.");
        }

        return busNumber;
    }

    /**
     * 특정 정류장을 경유하는 모든 운행 중인 버스 조회
     */
    public List<StationBusDTO> getBusesByStation(String stationId) {
        log.info("정류장 {}을 경유하는 버스 조회", stationId);

        // 1. 해당 정류장을 포함하는 모든 노선 찾기
        List<Route> routes = routeRepository.findByStationsStationId(new DBRef("stations", stationId));

        List<StationBusDTO> stationBuses = new ArrayList<>();

        for (Route route : routes) {
            // 2. 각 노선에 할당된 버스들 중 현재 운행 중인 버스 찾기
            List<Bus> routeBuses = busRepository.findByRouteId(route.getId());

            for (Bus bus : routeBuses) {
                // 3. 현재 진행 중인 운행 찾기
                List<BusOperation> activeOperations = busOperationRepository.findByBusIdAndStatusIn(
                        bus.getId(),
                        List.of(BusOperation.OperationStatus.IN_PROGRESS)
                );

                if (!activeOperations.isEmpty()) {
                    BusOperation operation = activeOperations.get(0);

                    // 4. 실시간 위치 정보 가져오기
                    DriverLocationUpdateDTO realtimeLocation = realtimeLocationService.getCurrentLocation(operation.getOperationId());

                    if (realtimeLocation != null) {
                        // 5. 해당 정류장까지의 도착 예상 시간 계산
                        String estimatedTime = calculateArrivalTime(bus.getBusNumber(), stationId);

                        StationBusDTO stationBus = StationBusDTO.builder()
                                .busNumber(bus.getBusNumber())
                                .busRealNumber(bus.getBusRealNumber())
                                .routeName(route.getRouteName())
                                .organizationId(bus.getOrganizationId())
                                .latitude(realtimeLocation.getLatitude())
                                .longitude(realtimeLocation.getLongitude())
                                .totalSeats(bus.getTotalSeats())
                                .currentPassengers(realtimeLocation.getCurrentPassengers())
                                .availableSeats(bus.getTotalSeats() - realtimeLocation.getCurrentPassengers())
                                .currentStationName(getCurrentStationName(bus))
                                .operationId(operation.getOperationId())
                                .isOperating(true)
                                .lastUpdateTime(realtimeLocation.getTimestamp())
                                .estimatedArrivalTime(estimatedTime)
                                .build();

                        stationBuses.add(stationBus);
                    }
                }
            }
        }

        // 6. 도착 예상 시간순으로 정렬
        stationBuses.sort((a, b) -> {
            if ("--분 --초".equals(a.getEstimatedArrivalTime())) return 1;
            if ("--분 --초".equals(b.getEstimatedArrivalTime())) return -1;
            return a.getEstimatedArrivalTime().compareTo(b.getEstimatedArrivalTime());
        });

        return stationBuses;
    }

    /**
     * 특정 버스의 노선 정보 및 현재 위치 조회
     */
    public BusRouteInfoDTO getBusRouteInfo(String busNumber, String organizationId) {
        log.info("버스 {} 노선 정보 조회", busNumber);

        // 1. 버스 정보 조회
        Bus bus;
        if (organizationId != null) {
            bus = busRepository.findByBusNumberAndOrganizationId(busNumber, organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busNumber));
        } else {
            bus = busRepository.findByBusNumber(busNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busNumber));
        }

        // 2. 현재 진행 중인 운행 찾기
        List<BusOperation> activeOperations = busOperationRepository.findByBusIdAndStatusIn(
                bus.getId(),
                List.of(BusOperation.OperationStatus.IN_PROGRESS)
        );

        if (activeOperations.isEmpty()) {
            throw new ResourceNotFoundException("버스 " + busNumber + "의 진행 중인 운행이 없습니다.");
        }

        BusOperation operation = activeOperations.getFirst();

        // 3. 실시간 위치 정보 가져오기
        DriverLocationUpdateDTO realtimeLocation = realtimeLocationService.getCurrentLocation(operation.getOperationId());
        if (realtimeLocation == null) {
            throw new ResourceNotFoundException("버스 " + busNumber + "의 실시간 위치 정보가 없습니다.");
        }

        // 4. 노선 정보 조회
        Route route = routeRepository.findById(bus.getRouteId().getId().toString())
                .orElseThrow(() -> new ResourceNotFoundException("노선 정보를 찾을 수 없습니다."));

        // 5. 현재 버스 위치와 정류장들의 정보 구성
        List<BusStationDTO> allStations = buildStationList(route, bus);

        // 6. 현재 정류장과 다음 정류장 찾기
        BusStationDTO currentStation = findCurrentStation(allStations);
        BusStationDTO nextStation = findNextStation(allStations);

        // 7. 다음 정류장까지의 시간 계산
        String estimatedTimeToNext = "--분 --초";
        int remainingSeconds = 0;

        if (nextStation != null) {
            try {
                BusArrivalEstimateResponseDTO estimate = kakaoApiService.getMultiWaysTimeEstimate(
                        busNumber, nextStation.getId());
                estimatedTimeToNext = estimate.getEstimatedTime();
                remainingSeconds = parseTimeToSeconds(estimatedTimeToNext);
            } catch (Exception e) {
                log.warn("다음 정류장까지의 시간 계산 실패: {}", e.getMessage());
            }
        }

        return BusRouteInfoDTO.builder()
                .busNumber(bus.getBusNumber())
                .busRealNumber(bus.getBusRealNumber())
                .routeName(route.getRouteName())
                .organizationId(bus.getOrganizationId())
                .latitude(realtimeLocation.getLatitude())
                .longitude(realtimeLocation.getLongitude())
                .currentStation(currentStation)
                .nextStation(nextStation)
                .estimatedTimeToNext(estimatedTimeToNext)
                .remainingSecondsToNext(remainingSeconds)
                .allStations(allStations)
                .totalSeats(bus.getTotalSeats())
                .currentPassengers(realtimeLocation.getCurrentPassengers())
                .availableSeats(bus.getTotalSeats() - realtimeLocation.getCurrentPassengers())
                .operationId(operation.getOperationId())
                .isOperating(true)
                .lastUpdateTime(realtimeLocation.getTimestamp())
                .build();
    }

    // Helper methods

    private String calculateArrivalTime(String busNumber, String stationId) {
        try {
            BusArrivalEstimateResponseDTO estimate = kakaoApiService.getMultiWaysTimeEstimate(busNumber, stationId);
            return estimate.getEstimatedTime();
        } catch (Exception e) {
            log.warn("도착 시간 계산 실패: 버스={}, 정류장={}, 오류={}", busNumber, stationId, e.getMessage());
            return "--분 --초";
        }
    }

    private String getCurrentStationName(Bus bus) {
        if (bus.getCurrentStationName() != null) {
            return bus.getCurrentStationName();
        }
        return "정보 없음";
    }

    private List<BusStationDTO> buildStationList(Route route, Bus bus) {
        List<BusStationDTO> stations = new ArrayList<>();
        int currentStationIndex = bus.getPrevStationIdx() != null ? bus.getPrevStationIdx() : -1;

        for (int i = 0; i < route.getStations().size(); i++) {
            Route.RouteStation routeStation = route.getStations().get(i);
            String stationId = routeStation.getStationId().getId().toString();

            Station station = stationRepository.findById(stationId).orElse(null);
            if (station != null) {
                boolean isPassed = i <= currentStationIndex;
                boolean isCurrent = i == currentStationIndex + 1; // 다음 정류장이 현재 향하고 있는 곳

                BusStationDTO stationDTO = BusStationDTO.builder()
                        .id(station.getId())
                        .name(station.getName())
                        .latitude(station.getLocation().getX())
                        .longitude(station.getLocation().getY())
                        .sequence(routeStation.getSequence())
                        .isPassed(isPassed)
                        .isCurrentStation(isCurrent)
                        .estimatedArrivalTime(isPassed ? "통과" : calculateArrivalTime(bus.getBusNumber(), stationId))
                        .build();

                stations.add(stationDTO);
            }
        }

        return stations;
    }

    private BusStationDTO findCurrentStation(List<BusStationDTO> stations) {
        return stations.stream()
                .filter(BusStationDTO::isCurrentStation)
                .findFirst()
                .orElse(null);
    }

    private BusStationDTO findNextStation(List<BusStationDTO> stations) {
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).isCurrentStation() && i + 1 < stations.size()) {
                return stations.get(i + 1);
            }
        }
        return null;
    }

    private int parseTimeToSeconds(String timeString) {
        if ("--분 --초".equals(timeString)) {
            return 0;
        }

        try {
            String[] parts = timeString.split(" ");
            int minutes = 0, seconds = 0;

            for (String part : parts) {
                if (part.contains("분")) {
                    minutes = Integer.parseInt(part.replace("분", ""));
                } else if (part.contains("초")) {
                    seconds = Integer.parseInt(part.replace("초", ""));
                }
            }

            return minutes * 60 + seconds;
        } catch (Exception e) {
            log.warn("시간 파싱 실패: {}", timeString);
            return 0;
        }
    }
}