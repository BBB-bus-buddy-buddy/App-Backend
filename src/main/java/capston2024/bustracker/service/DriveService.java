package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.*;
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
        try {
            log.info("운행 시작 처리 - 운행ID: {}, 운전자: {}", requestDTO.getOperationId(), driverEmail);

            // 1. 운전자 정보 조회
            User driver = userRepository.findByEmail(driverEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

            // 2. 운행 일정 조회 - 먼저 operationId로 시도, 실패하면 _id로 시도
            BusOperation operation = busOperationRepository
                    .findByOperationIdAndOrganizationId(requestDTO.getOperationId(), organizationId)
                    .orElseGet(() -> {
                        log.info("operationId로 조회 실패, MongoDB _id로 재시도: {}", requestDTO.getOperationId());
                        return busOperationRepository
                                .findByIdAndOrganizationId(requestDTO.getOperationId(), organizationId)
                                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + requestDTO.getOperationId()));
                    });

            log.info("운행 일정 조회 성공 - DB ID: {}, Operation ID: {}", operation.getId(), operation.getOperationId());

            // 3. 운행 상태 확인
            if (!DRIVE_STATUS_SCHEDULED.equals(operation.getStatus())) {
                throw new BusinessException("이미 시작되었거나 완료된 운행입니다. 현재 상태: " + operation.getStatus());
            }

            // 4. DBRef 유효성 검증
            if (operation.getDriverId() == null || operation.getDriverId().getId() == null) {
                log.error("운행 일정의 드라이버 정보가 없습니다. operationId: {}", operation.getId());
                throw new BusinessException("운행 일정에 드라이버 정보가 없습니다.");
            }

            if (operation.getBusId() == null || operation.getBusId().getId() == null) {
                log.error("운행 일정의 버스 정보가 없습니다. operationId: {}", operation.getId());
                throw new BusinessException("운행 일정에 버스 정보가 없습니다.");
            }

            // 5. 운전자 확인
            String operationDriverId = operation.getDriverId().getId().toString();
            if (!operationDriverId.equals(driver.getId())) {
                log.error("운전자 불일치 - 예정: {}, 실제: {}", operationDriverId, driver.getId());
                throw new BusinessException("해당 운행의 배정된 운전자가 아닙니다.");
            }

            // 6. 버스 정보 조회
            String busId = operation.getBusId().getId().toString();
            Bus bus = busRepository.findById(busId)
                    .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busId));

            // 7. 이미 운행 중인 버스인지 확인
            if (bus.isOperate()) {
                throw new BusinessException("이미 운행 중인 버스입니다: " + bus.getBusNumber());
            }

            // 8. 출발 시간 검증
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

            // 9. 출발지 도착 확인 (null 체크 추가)
            if (requestDTO.getCurrentLocation() != null) {
                try {
                    validateStartLocation(bus, requestDTO.getCurrentLocation());
                } catch (Exception e) {
                    log.error("출발지 위치 검증 실패: {}", e.getMessage());
                    // 위치 검증 실패시에도 운행은 시작할 수 있도록 처리
                    log.warn("출발지 위치 검증을 건너뜁니다.");
                }
            }

            // 10. 버스 상태 업데이트 - Bus의 isOperate를 true로
            bus.setOperate(true);
            bus.setPrevStationIdx(0); // 첫 정류장부터 시작
            busRepository.save(bus);

            // 11. 운행 상태 업데이트 - BusOperation의 status를 IN_PROGRESS로
            operation.setStatus(DRIVE_STATUS_IN_PROGRESS);
            operation.setActualStart(now); // 실제 시작 시간 기록
            busOperationRepository.save(operation);

            // 12. 응답 DTO 생성
            return buildDriveStatusDTO(operation, bus, driver, requestDTO.isEarlyStart(), "운행이 시작되었습니다.");

        } catch (Exception e) {
            log.error("운행 시작 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 운행 종료 - BusOperation과 Bus 상태를 통합 관리
     */
    @Transactional
    public DriveStatusDTO endDrive(DriveEndRequestDTO requestDTO, String driverEmail, String organizationId) {
        try {
            log.info("운행 종료 처리 - 운행ID: {}, 운전자: {}", requestDTO.getOperationId(), driverEmail);

            // 1. 운전자 정보 조회
            User driver = userRepository.findByEmail(driverEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

            // 2. 운행 일정 조회 - 먼저 operationId로 시도, 실패하면 _id로 시도
            BusOperation operation = busOperationRepository
                    .findByOperationIdAndOrganizationId(requestDTO.getOperationId(), organizationId)
                    .orElseGet(() -> {
                        log.info("operationId로 조회 실패, MongoDB _id로 재시도: {}", requestDTO.getOperationId());
                        return busOperationRepository
                                .findByIdAndOrganizationId(requestDTO.getOperationId(), organizationId)
                                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + requestDTO.getOperationId()));
                    });

            // 3. 운행 상태 확인
            if (!DRIVE_STATUS_IN_PROGRESS.equals(operation.getStatus())) {
                throw new BusinessException("진행 중인 운행이 아닙니다. 현재 상태: " + operation.getStatus());
            }

            // 4. DBRef 유효성 검증
            if (operation.getDriverId() == null || operation.getDriverId().getId() == null) {
                throw new BusinessException("운행 일정에 드라이버 정보가 없습니다.");
            }

            if (operation.getBusId() == null || operation.getBusId().getId() == null) {
                throw new BusinessException("운행 일정에 버스 정보가 없습니다.");
            }

            // 5. 운전자 확인
            if (!operation.getDriverId().getId().toString().equals(driver.getId())) {
                throw new BusinessException("해당 운행의 배정된 운전자가 아닙니다.");
            }

            // 6. 버스 정보 조회
            String busId = operation.getBusId().getId().toString();
            Bus bus = busRepository.findById(busId)
                    .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busId));

            // 7. 운행 중인 버스인지 확인
            if (!bus.isOperate()) {
                log.warn("이미 운행이 종료된 버스입니다: {}", bus.getBusNumber());
            }

            // 8. 버스 상태 업데이트 - Bus의 isOperate를 false로
            bus.setOperate(false);
            bus.setOccupiedSeats(0); // 승객 수 초기화
            bus.setAvailableSeats(bus.getTotalSeats());
            busRepository.save(bus);

            // 9. 운행 상태 업데이트 - BusOperation의 status를 COMPLETED로
            operation.setStatus(DRIVE_STATUS_COMPLETED);
            operation.setActualEnd(LocalDateTime.now()); // 실제 종료 시간 기록
            busOperationRepository.save(operation);

            // 10. 응답 DTO 생성
            String message = requestDTO.getEndReason() != null ?
                    "운행이 종료되었습니다. 사유: " + requestDTO.getEndReason() :
                    "운행이 종료되었습니다.";

            return buildDriveStatusDTO(operation, bus, driver, false, message);

        } catch (Exception e) {
            log.error("운행 종료 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 운행 중 위치 업데이트
     * @deprecated WebSocket을 통한 실시간 위치 업데이트로 대체됨
     *
     * 위치 업데이트는 이제 다음과 같이 처리됩니다:
     * 1. 운전자 앱에서 WebSocket으로 위치 정보 전송
     * 2. BusDriverWebSocketHandler가 메시지 수신
     * 3. BusService.updateBusLocation()으로 pendingLocationUpdates에 저장
     * 4. BusService.flushLocationUpdates()가 주기적으로(10초마다) DB에 반영
     */
    @Deprecated
    public Map<String, Object> updateLocation(DriveLocationUpdateDTO requestDTO, String driverEmail, String organizationId) {
        log.warn("Deprecated 메서드 호출 - updateLocation은 더 이상 사용되지 않습니다. WebSocket을 사용하세요.");

        Map<String, Object> response = new HashMap<>();
        response.put("updated", false);
        response.put("warning", "이 메서드는 더 이상 사용되지 않습니다. WebSocket을 통한 실시간 위치 업데이트를 사용하세요.");
        return response;
    }

    /**
     * 다음 운행 정보 조회
     */
    public DriveStatusDTO getNextDrive(String currentOperationId, String busNumber, String driverEmail, String organizationId) {
        try {
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
                            op.getBusId() != null &&
                            op.getBusId().getId() != null &&
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

        } catch (Exception e) {
            log.error("다음 운행 정보 조회 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 운행 상태 조회 - 프론트엔드 drive.js의 getDriveStatus와 호환
     */
    public DriveStatusDTO getDriveStatus(String operationId, String driverEmail, String organizationId) {
        try {
            log.info("운행 상태 조회 - 운행ID: {}, 운전자: {}", operationId, driverEmail);

            // 1. 운전자 정보 조회
            User driver = userRepository.findByEmail(driverEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

            // 2. 운행 일정 조회 - 먼저 operationId로 시도, 실패하면 _id로 시도
            BusOperation operation = busOperationRepository
                    .findByOperationIdAndOrganizationId(operationId, organizationId)
                    .orElseGet(() -> {
                        log.info("operationId로 조회 실패, MongoDB _id로 재시도: {}", operationId);
                        return busOperationRepository
                                .findByIdAndOrganizationId(operationId, organizationId)
                                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + operationId));
                    });

            // 3. DBRef 유효성 검증
            if (operation.getDriverId() == null || operation.getDriverId().getId() == null) {
                throw new BusinessException("운행 일정에 드라이버 정보가 없습니다.");
            }

            if (operation.getBusId() == null || operation.getBusId().getId() == null) {
                throw new BusinessException("운행 일정에 버스 정보가 없습니다.");
            }

            // 4. 운전자 확인
            if (!operation.getDriverId().getId().toString().equals(driver.getId())) {
                throw new BusinessException("해당 운행의 배정된 운전자가 아닙니다.");
            }

            // 5. 버스 정보 조회
            String busId = operation.getBusId().getId().toString();
            Bus bus = busRepository.findById(busId)
                    .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busId));

            // 6. 상태에 따른 메시지 생성
            String message = switch (operation.getStatus()) {
                case DRIVE_STATUS_SCHEDULED -> "운행 예정입니다.";
                case DRIVE_STATUS_IN_PROGRESS -> "운행 중입니다.";
                case DRIVE_STATUS_COMPLETED -> "운행이 완료되었습니다.";
                case DRIVE_STATUS_CANCELLED -> "운행이 취소되었습니다.";
                default -> "알 수 없는 상태입니다.";
            };

            // 7. 응답 DTO 생성
            return buildDriveStatusDTO(operation, bus, driver, false, message);

        } catch (Exception e) {
            log.error("운행 상태 조회 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 운행 시작 가능 여부 확인 - 프론트엔드의 canStartDrive 헬퍼 함수와 호환
     */
    public Map<String, Object> canStartDrive(String operationId, String driverEmail, String organizationId,
                                             boolean allowEarlyStart, int earlyStartMinutes) {
        try {
            // operationId로 먼저 시도, 실패하면 _id로 시도
            BusOperation operation = busOperationRepository
                    .findByOperationIdAndOrganizationId(operationId, organizationId)
                    .orElseGet(() -> busOperationRepository
                            .findByIdAndOrganizationId(operationId, organizationId)
                            .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + operationId)));

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

        } catch (Exception e) {
            log.error("운행 시작 가능 여부 확인 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 출발지 위치 검증
     */
    private void validateStartLocation(Bus bus, DriveStartRequestDTO.LocationInfo currentLocation) {
        try {
            if (bus.getRouteId() == null || bus.getRouteId().getId() == null) {
                log.warn("버스에 할당된 노선이 없습니다. 위치 검증을 건너뜁니다.");
                return;
            }

            // 라우트 정보 조회
            String routeId = bus.getRouteId().getId().toString();
            Route route = routeRepository.findById(routeId).orElse(null);

            if (route == null) {
                log.warn("노선을 찾을 수 없습니다. 위치 검증을 건너뜁니다. routeId: {}", routeId);
                return;
            }

            // 첫 번째 정류장이 출발지
            if (route.getStations() == null || route.getStations().isEmpty()) {
                log.warn("노선에 정류장 정보가 없습니다. 위치 검증을 건너뜁니다.");
                return;
            }

            Route.RouteStation firstStation = route.getStations().get(0);
            if (firstStation.getStationId() == null || firstStation.getStationId().getId() == null) {
                log.warn("첫 번째 정류장 정보가 올바르지 않습니다. 위치 검증을 건너뜁니다.");
                return;
            }

            String firstStationId = firstStation.getStationId().getId().toString();
            Station startStation = stationRepository.findById(firstStationId).orElse(null);

            if (startStation == null || startStation.getLocation() == null) {
                log.warn("출발지 정류장을 찾을 수 없거나 위치 정보가 없습니다. 위치 검증을 건너뜁니다.");
                return;
            }

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

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("출발지 위치 검증 중 예외 발생: {}", e.getMessage(), e);
            // 다른 예외는 로그만 남기고 진행
        }
    }

    /**
     * DriveStatusDTO 생성
     */
    private DriveStatusDTO buildDriveStatusDTO(BusOperation operation, Bus bus, User driver, boolean isEarlyStart, String message) {
        try {
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
            if (bus.getRouteId() != null && bus.getRouteId().getId() != null) {
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

        } catch (Exception e) {
            log.error("DriveStatusDTO 생성 중 오류: {}", e.getMessage(), e);
            // 최소한의 정보만으로 DTO 생성
            return DriveStatusDTO.builder()
                    .operationId(operation.getId())
                    .status(operation.getStatus())
                    .busId(bus.getId())
                    .busNumber(bus.getBusNumber())
                    .driverId(driver.getId())
                    .driverName(driver.getName())
                    .message(message)
                    .hasNextDrive(false)
                    .build();
        }
    }

    /**
     * 출발지/도착지 정보 설정
     */
    private void setLocationInfo(DriveStatusDTO.DriveStatusDTOBuilder builder, Route route) {
        try {
            if (route.getStations() != null && !route.getStations().isEmpty()) {
                // 출발지 (첫 번째 정류장)
                Route.RouteStation firstRouteStation = route.getStations().get(0);
                if (firstRouteStation.getStationId() != null && firstRouteStation.getStationId().getId() != null) {
                    String firstStationId = firstRouteStation.getStationId().getId().toString();
                    Station startStation = stationRepository.findById(firstStationId).orElse(null);
                    if (startStation != null && startStation.getLocation() != null) {
                        builder.startLocation(DriveStatusDTO.LocationInfo.builder()
                                .name(startStation.getName())
                                .latitude(startStation.getLocation().getY())
                                .longitude(startStation.getLocation().getX())
                                .build());
                    }
                }

                // 도착지 (마지막 정류장)
                int lastIndex = route.getStations().size() - 1;
                Route.RouteStation lastRouteStation = route.getStations().get(lastIndex);
                if (lastRouteStation.getStationId() != null && lastRouteStation.getStationId().getId() != null) {
                    String lastStationId = lastRouteStation.getStationId().getId().toString();
                    Station endStation = stationRepository.findById(lastStationId).orElse(null);
                    if (endStation != null && endStation.getLocation() != null) {
                        builder.endLocation(DriveStatusDTO.LocationInfo.builder()
                                .name(endStation.getName())
                                .latitude(endStation.getLocation().getY())
                                .longitude(endStation.getLocation().getX())
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("출발지/도착지 정보 설정 중 오류: {}", e.getMessage());
            // 오류 발생시 해당 정보 없이 진행
        }
    }
}