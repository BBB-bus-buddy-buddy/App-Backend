package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.BusRealTimeLocationDTO;
import capston2024.bustracker.config.dto.DriveStartRequestDTO;
import capston2024.bustracker.config.dto.DriveEndRequestDTO;
import capston2024.bustracker.config.dto.DriveLocationUpdateDTO;
import capston2024.bustracker.config.dto.DriveStatusDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.domain.Route;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusOperationRepository;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.RouteRepository;
import capston2024.bustracker.repository.StationRepository;
import capston2024.bustracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DriveService {

    private final BusOperationRepository busOperationRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final BusService busService;
    private final KakaoApiService kakaoApiService;

    // 운행 상태 상수 - 프론트엔드 DRIVE_STATUS와 매칭
    public static final String DRIVE_STATUS_SCHEDULED = "SCHEDULED";      // 예정됨
    public static final String DRIVE_STATUS_IN_PROGRESS = "IN_PROGRESS";  // 진행 중
    public static final String DRIVE_STATUS_COMPLETED = "COMPLETED";      // 완료됨
    public static final String DRIVE_STATUS_CANCELLED = "CANCELLED";      // 취소됨

    // 출발지 도착 허용 반경 (미터)
    private static final double ARRIVAL_THRESHOLD_METERS = 50.0;
    // 조기 출발 허용 시간 (분)
    private static final int EARLY_START_ALLOWED_MINUTES = 10;

    /**
     * 운행 시작 - BusOperation과 Bus 상태를 통합 관리
     */
    @Transactional
    public DriveStatusDTO startDrive(DriveStartRequestDTO requestDTO, String driverEmail, String organizationId) {
        log.info("운행 시작 처리 - 운행ID: {}, 운전자: {}", requestDTO.getOperationId(), driverEmail);

        // 1. 운전자 정보 조회
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

        // 2. 운행 일정 조회 및 검증
        BusOperation operation = busOperationRepository
                .findByIdAndOrganizationId(requestDTO.getOperationId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + requestDTO.getOperationId()));

        // 3. 운행 상태 확인
        if (!DRIVE_STATUS_SCHEDULED.equals(operation.getStatus())) {
            throw new BusinessException("이미 시작되었거나 완료된 운행입니다. 현재 상태: " + operation.getStatus());
        }

        // 4. 운전자 확인
        if (!operation.getDriverId().getId().toString().equals(driver.getId())) {
            throw new BusinessException("해당 운행의 배정된 운전자가 아닙니다.");
        }

        // 5. 버스 정보 조회
        String busId = operation.getBusId().getId().toString();
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busId));

        // 6. 이미 운행 중인 버스인지 확인
        if (bus.isOperate()) {
            throw new BusinessException("이미 운행 중인 버스입니다: " + bus.getBusNumber());
        }

        // 7. 출발 시간 검증
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledStart = operation.getScheduledStart();

        if (requestDTO.isEarlyStart()) {
            // 조기 출발인 경우 허용 시간 확인
            LocalDateTime earliestAllowed = scheduledStart.minusMinutes(EARLY_START_ALLOWED_MINUTES);
            if (now.isBefore(earliestAllowed)) {
                throw new BusinessException(String.format("조기 출발은 예정 시간 %d분 전부터만 가능합니다.", EARLY_START_ALLOWED_MINUTES));
            }
        } else {
            // 정상 출발인 경우 정각 이후인지 확인
            if (now.isBefore(scheduledStart)) {
                throw new BusinessException("아직 출발 시간이 되지 않았습니다. 예정 시간: " + scheduledStart);
            }
        }

        // 8. 출발지 도착 확인
        validateStartLocation(bus, requestDTO.getCurrentLocation());

        // 9. 버스 상태 업데이트 - Bus의 isOperate를 true로
        bus.setOperate(true);
        bus.setPrevStationIdx(0); // 첫 정류장부터 시작
        busRepository.save(bus);

        // 10. 운행 상태 업데이트 - BusOperation의 status를 IN_PROGRESS로
        operation.setStatus(DRIVE_STATUS_IN_PROGRESS);
        operation.setActualStart(now); // 실제 시작 시간 기록
        busOperationRepository.save(operation);

        // 11. 응답 DTO 생성
        return buildDriveStatusDTO(operation, bus, driver, requestDTO.isEarlyStart(), "운행이 시작되었습니다.");
    }

    /**
     * 운행 종료 - BusOperation과 Bus 상태를 통합 관리
     */
    @Transactional
    public DriveStatusDTO endDrive(DriveEndRequestDTO requestDTO, String driverEmail, String organizationId) {
        log.info("운행 종료 처리 - 운행ID: {}, 운전자: {}", requestDTO.getOperationId(), driverEmail);

        // 1. 운전자 정보 조회
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

        // 2. 운행 일정 조회 및 검증
        BusOperation operation = busOperationRepository
                .findByIdAndOrganizationId(requestDTO.getOperationId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + requestDTO.getOperationId()));

        // 3. 운행 상태 확인
        if (!DRIVE_STATUS_IN_PROGRESS.equals(operation.getStatus())) {
            throw new BusinessException("진행 중인 운행이 아닙니다. 현재 상태: " + operation.getStatus());
        }

        // 4. 운전자 확인
        if (!operation.getDriverId().getId().toString().equals(driver.getId())) {
            throw new BusinessException("해당 운행의 배정된 운전자가 아닙니다.");
        }

        // 5. 버스 정보 조회
        String busId = operation.getBusId().getId().toString();
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busId));

        // 6. 운행 중인 버스인지 확인
        if (!bus.isOperate()) {
            log.warn("이미 운행이 종료된 버스입니다: {}", bus.getBusNumber());
        }

        // 7. 버스 상태 업데이트 - Bus의 isOperate를 false로
        bus.setOperate(false);
        bus.setOccupiedSeats(0); // 승객 수 초기화
        bus.setAvailableSeats(bus.getTotalSeats());
        busRepository.save(bus);

        // 8. 운행 상태 업데이트 - BusOperation의 status를 COMPLETED로
        operation.setStatus(DRIVE_STATUS_COMPLETED);
        operation.setActualEnd(LocalDateTime.now()); // 실제 종료 시간 기록
        busOperationRepository.save(operation);

        // 9. 응답 DTO 생성
        String message = requestDTO.getEndReason() != null ?
                "운행이 종료되었습니다. 사유: " + requestDTO.getEndReason() :
                "운행이 종료되었습니다.";

        return buildDriveStatusDTO(operation, bus, driver, false, message);
    }

    /**
     * 운행 중 위치 업데이트
     */
    @Transactional
    public Map<String, Object> updateLocation(DriveLocationUpdateDTO requestDTO, String driverEmail, String organizationId) {
        log.debug("위치 업데이트 처리 - 운행ID: {}, 버스: {}", requestDTO.getOperationId(), requestDTO.getBusNumber());

        // 1. 운행 일정 조회 및 검증
        BusOperation operation = busOperationRepository
                .findByIdAndOrganizationId(requestDTO.getOperationId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + requestDTO.getOperationId()));

        // 2. 운행 상태 확인
        if (!DRIVE_STATUS_IN_PROGRESS.equals(operation.getStatus())) {
            throw new BusinessException("진행 중인 운행이 아닙니다. 현재 상태: " + operation.getStatus());
        }

        // 3. 버스 정보 조회
        String busId = operation.getBusId().getId().toString();
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busId));

        // 4. 버스 번호 확인
        if (!bus.getBusNumber().equals(requestDTO.getBusNumber())) {
            throw new BusinessException("운행 일정의 버스 번호와 일치하지 않습니다.");
        }

        // 5. BusService를 통해 위치 업데이트 (BusRealTimeLocationDTO로 변환)
        BusRealTimeLocationDTO busLocationDTO = new BusRealTimeLocationDTO(
                requestDTO.getBusNumber(),
                organizationId,
                requestDTO.getLocation().getLatitude(),
                requestDTO.getLocation().getLongitude(),
                bus.getOccupiedSeats(),
                requestDTO.getLocation().getTimestamp()
        );

        busService.updateBusLocation(busLocationDTO);

        // 6. 다음 정류장 정보 조회
        Map<String, Object> nextStopInfo = getNextStopInfo(bus);

        // 7. 응답 데이터 구성
        Map<String, Object> response = new HashMap<>();
        response.put("updated", true);
        response.put("busNumber", bus.getBusNumber());
        response.put("currentLocation", Map.of(
                "latitude", requestDTO.getLocation().getLatitude(),
                "longitude", requestDTO.getLocation().getLongitude()
        ));

        if (nextStopInfo != null) {
            response.put("nextStop", nextStopInfo);
        }

        // 목적지 근처 도착 여부 확인
        boolean isNearDestination = checkNearDestination(bus, requestDTO.getLocation());
        response.put("isNearDestination", isNearDestination);

        return response;
    }

    /**
     * 다음 운행 정보 조회
     */
    public DriveStatusDTO getNextDrive(String currentOperationId, String busNumber, String driverEmail, String organizationId) {
        log.info("다음 운행 정보 조회 - 현재운행: {}, 버스: {}, 운전자: {}", currentOperationId, busNumber, driverEmail);

        // 운전자 정보 조회
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

        // 버스 정보 조회
        Bus bus = busService.getBusByNumberAndOrganization(busNumber, organizationId);

        // 현재 날짜의 남은 운행 일정 조회
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = now.toLocalDate().atTime(LocalTime.MAX);

        List<BusOperation> todayOperations = busOperationRepository
                .findByDriverIdAndOrganizationId(driver.getId(), organizationId)
                .stream()
                .filter(op -> op.getScheduledStart().isAfter(now) &&
                        op.getScheduledStart().isBefore(endOfDay) &&
                        DRIVE_STATUS_SCHEDULED.equals(op.getStatus()) &&
                        op.getBusId().getId().toString().equals(bus.getId()))
                .sorted((a, b) -> a.getScheduledStart().compareTo(b.getScheduledStart()))
                .toList();

        if (todayOperations.isEmpty()) {
            return null;
        }

        // 가장 가까운 다음 운행
        BusOperation nextOperation = todayOperations.get(0);

        DriveStatusDTO nextDrive = buildDriveStatusDTO(nextOperation, bus, driver, false, "다음 운행 일정입니다.");
        nextDrive.setHasNextDrive(true);

        return nextDrive;
    }

    /**
     * 운행 상태 조회 - 프론트엔드 drive.js의 getDriveStatus와 호환
     */
    public DriveStatusDTO getDriveStatus(String operationId, String driverEmail, String organizationId) {
        log.info("운행 상태 조회 - 운행ID: {}, 운전자: {}", operationId, driverEmail);

        // 1. 운전자 정보 조회
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

        // 2. 운행 일정 조회 및 검증
        BusOperation operation = busOperationRepository
                .findByIdAndOrganizationId(operationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + operationId));

        // 3. 운전자 확인
        if (!operation.getDriverId().getId().toString().equals(driver.getId())) {
            throw new BusinessException("해당 운행의 배정된 운전자가 아닙니다.");
        }

        // 4. 버스 정보 조회
        String busId = operation.getBusId().getId().toString();
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busId));

        // 5. 상태에 따른 메시지 생성
        String message = switch (operation.getStatus()) {
            case DRIVE_STATUS_SCHEDULED -> "운행 예정입니다.";
            case DRIVE_STATUS_IN_PROGRESS -> "운행 중입니다.";
            case DRIVE_STATUS_COMPLETED -> "운행이 완료되었습니다.";
            case DRIVE_STATUS_CANCELLED -> "운행이 취소되었습니다.";
            default -> "알 수 없는 상태입니다.";
        };

        // 6. 응답 DTO 생성
        return buildDriveStatusDTO(operation, bus, driver, false, message);
    }

    /**
     * 운행 시작 가능 여부 확인 - 프론트엔드의 canStartDrive 헬퍼 함수와 호환
     */
    public Map<String, Object> canStartDrive(String operationId, String driverEmail, String organizationId,
                                             boolean allowEarlyStart, int earlyStartMinutes) {
        BusOperation operation = busOperationRepository
                .findByIdAndOrganizationId(operationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + operationId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledStart = operation.getScheduledStart();

        Map<String, Object> result = new HashMap<>();

        if (allowEarlyStart) {
            LocalDateTime earliestStart = scheduledStart.minusMinutes(earlyStartMinutes);

            if (now.isBefore(earliestStart)) {
                long minutesUntilEarly = java.time.Duration.between(now, earliestStart).toMinutes();
                result.put("canStart", false);
                result.put("message", String.format("조기 출발은 %d분 후부터 가능합니다.", minutesUntilEarly));
            } else {
                result.put("canStart", true);
                result.put("message", "조기 출발이 가능합니다.");
            }
        } else {
            if (now.isBefore(scheduledStart)) {
                long minutesUntilStart = java.time.Duration.between(now, scheduledStart).toMinutes();
                result.put("canStart", false);
                result.put("message", String.format("출발 시간까지 %d분 남았습니다.", minutesUntilStart));
            } else {
                result.put("canStart", true);
                result.put("message", "운행을 시작할 수 있습니다.");
            }
        }

        return result;
    }

    /**
     * 출발지 위치 검증
     */
    private void validateStartLocation(Bus bus, DriveStartRequestDTO.LocationInfo currentLocation) {
        if (bus.getRouteId() == null) {
            throw new BusinessException("버스에 할당된 노선이 없습니다.");
        }

        // 라우트 정보 조회
        String routeId = bus.getRouteId().getId().toString();
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("노선을 찾을 수 없습니다: " + routeId));

        // 첫 번째 정류장이 출발지
        if (route.getStations() == null || route.getStations().isEmpty()) {
            throw new BusinessException("노선에 정류장 정보가 없습니다.");
        }

        String firstStationId = route.getStations().get(0).getStationId().getId().toString();
        Station startStation = stationRepository.findById(firstStationId)
                .orElseThrow(() -> new ResourceNotFoundException("출발지 정류장을 찾을 수 없습니다: " + firstStationId));

        // BusService의 거리 계산 메서드 사용
        double distance = busService.calculateDistance(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                startStation.getLocation().getY(), startStation.getLocation().getX()
        );

        if (distance > ARRIVAL_THRESHOLD_METERS) {
            throw new BusinessException(String.format(
                    "출발지에서 너무 멀리 있습니다. 현재 거리: %.0fm (허용: %.0fm)",
                    distance, ARRIVAL_THRESHOLD_METERS));
        }

        log.info("출발지 도착 확인 완료 - 정류장: {}, 거리: {}m", startStation.getName(), String.format("%.0f", distance));
    }

    /**
     * 다음 정류장 정보 조회
     */
    private Map<String, Object> getNextStopInfo(Bus bus) {
        try {
            if (bus.getRouteId() == null) {
                return null;
            }

            String routeId = bus.getRouteId().getId().toString();
            Route route = routeRepository.findById(routeId).orElse(null);

            if (route == null || route.getStations() == null || route.getStations().isEmpty()) {
                return null;
            }

            // 현재 정류장 인덱스 다음 정류장 찾기
            int nextIdx = bus.getPrevStationIdx() + 1;
            if (nextIdx >= route.getStations().size()) {
                // 마지막 정류장에 도달
                return Map.of(
                        "name", "종점",
                        "isLastStop", true
                );
            }

            String nextStationId = route.getStations().get(nextIdx).getStationId().getId().toString();
            Station nextStation = stationRepository.findById(nextStationId).orElse(null);

            if (nextStation == null) {
                return null;
            }

            // 다음 정류장까지 예상 시간 계산 (KakaoApiService 사용)
            Map<String, Object> stopInfo = new HashMap<>();
            stopInfo.put("name", nextStation.getName());
            stopInfo.put("sequence", nextIdx + 1);
            stopInfo.put("totalStops", route.getStations().size());
            stopInfo.put("isLastStop", nextIdx == route.getStations().size() - 1);

            try {
                String estimatedTime = kakaoApiService.getMultiWaysTimeEstimate(
                        bus.getBusNumber(),
                        nextStationId
                ).getEstimatedTime();
                stopInfo.put("estimatedTime", estimatedTime);
            } catch (Exception e) {
                log.warn("다음 정류장 도착 시간 예측 실패: {}", e.getMessage());
                stopInfo.put("estimatedTime", "계산 중...");
            }

            return stopInfo;

        } catch (Exception e) {
            log.error("다음 정류장 정보 조회 중 오류: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 목적지 근처 도착 여부 확인
     */
    private boolean checkNearDestination(Bus bus, DriveLocationUpdateDTO.LocationInfo currentLocation) {
        try {
            if (bus.getRouteId() == null) {
                return false;
            }

            String routeId = bus.getRouteId().getId().toString();
            Route route = routeRepository.findById(routeId).orElse(null);

            if (route == null || route.getStations() == null || route.getStations().isEmpty()) {
                return false;
            }

            // 마지막 정류장이 도착지
            int lastIdx = route.getStations().size() - 1;
            String lastStationId = route.getStations().get(lastIdx).getStationId().getId().toString();
            Station endStation = stationRepository.findById(lastStationId).orElse(null);

            if (endStation == null) {
                return false;
            }

            double distance = busService.calculateDistance(
                    currentLocation.getLatitude(), currentLocation.getLongitude(),
                    endStation.getLocation().getY(), endStation.getLocation().getX()
            );

            // 도착지 100m 이내면 true
            return distance <= 100.0;

        } catch (Exception e) {
            log.error("목적지 도착 확인 중 오류: {}", e.getMessage());
            return false;
        }
    }

    /**
     * DriveStatusDTO 생성
     */
    private DriveStatusDTO buildDriveStatusDTO(BusOperation operation, Bus bus, User driver, boolean isEarlyStart, String message) {
        DriveStatusDTO.DriveStatusDTOBuilder builder = DriveStatusDTO.builder()
                .operationId(operation.getId())
                .status(operation.getStatus())
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busRealNumber(bus.getBusRealNumber())
                .busIsOperate(bus.isOperate())
                .driverId(driver.getId())
                .driverName(driver.getName())
                .scheduledStart(operation.getScheduledStart())
                .scheduledEnd(operation.getScheduledEnd())
                .isEarlyStart(isEarlyStart)
                .message(message)
                .hasNextDrive(false);

        // 실제 시작/종료 시간 설정
        if (operation.getActualStart() != null) {
            builder.actualStart(operation.getActualStart());
        }
        if (operation.getActualEnd() != null) {
            builder.actualEnd(operation.getActualEnd());
        }

        // 라우트 정보 설정
        if (bus.getRouteId() != null) {
            String routeId = bus.getRouteId().getId().toString();
            builder.routeId(routeId);

            Route route = routeRepository.findById(routeId).orElse(null);
            if (route != null) {
                builder.routeName(route.getRouteName());

                // 출발지/도착지 정보 설정
                setLocationInfo(builder, route);
            }
        }

        return builder.build();
    }

    /**
     * 출발지/도착지 정보 설정
     */
    private void setLocationInfo(DriveStatusDTO.DriveStatusDTOBuilder builder, Route route) {
        if (route.getStations() != null && !route.getStations().isEmpty()) {
            // 출발지 (첫 번째 정류장)
            String firstStationId = route.getStations().get(0).getStationId().getId().toString();
            Station startStation = stationRepository.findById(firstStationId).orElse(null);
            if (startStation != null) {
                builder.startLocation(DriveStatusDTO.LocationInfo.builder()
                        .name(startStation.getName())
                        .latitude(startStation.getLocation().getY())
                        .longitude(startStation.getLocation().getX())
                        .build());
            }

            // 도착지 (마지막 정류장)
            int lastIndex = route.getStations().size() - 1;
            String lastStationId = route.getStations().get(lastIndex).getStationId().getId().toString();
            Station endStation = stationRepository.findById(lastStationId).orElse(null);
            if (endStation != null) {
                builder.endLocation(DriveStatusDTO.LocationInfo.builder()
                        .name(endStation.getName())
                        .latitude(endStation.getLocation().getY())
                        .longitude(endStation.getLocation().getX())
                        .build());
            }
        }
    }
}