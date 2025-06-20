// src/main/java/capston2024/bustracker/service/BusOperationService.java
package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.OperationPlanDTO;
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
import com.mongodb.DBRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BusOperationService {

    private final BusOperationRepository busOperationRepository;
    private final BusRepository busRepository;
    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;

    /**
     * 운행 일정 생성
     */
    @Transactional
    public List<OperationPlanDTO> createOperationPlan(OperationPlanDTO dto, String organizationId) {
        log.info("운행 일정 생성 요청 - 조직: {}, 버스: {}, 기사: {}",
                organizationId, dto.getBusId(), dto.getDriverId());

        // 입력값 검증
        validateOperationPlan(dto, organizationId);

        List<BusOperation> operations = new ArrayList<>();

        // 단일 일정 생성
        LocalDateTime scheduledStart = LocalDateTime.of(dto.getOperationDate(), dto.getStartTime());
        LocalDateTime scheduledEnd = LocalDateTime.of(dto.getOperationDate(), dto.getEndTime());

        // 중복 일정 체크
        checkScheduleConflict(dto.getBusId(), dto.getDriverId(), scheduledStart, scheduledEnd);

        BusOperation operation = createSingleOperation(dto, scheduledStart, scheduledEnd, organizationId);
        operations.add(operation);

        // 반복 일정 생성
        if (dto.isRecurring() && dto.getRecurringWeeks() != null && dto.getRecurringWeeks() > 0) {
            String parentId = operation.getOperationId();

            for (int week = 1; week <= dto.getRecurringWeeks(); week++) {
                LocalDate nextDate = dto.getOperationDate().plusWeeks(week);
                LocalDateTime nextStart = LocalDateTime.of(nextDate, dto.getStartTime());
                LocalDateTime nextEnd = LocalDateTime.of(nextDate, dto.getEndTime());

                // 각 반복 일정에 대해서도 중복 체크
                try {
                    checkScheduleConflict(dto.getBusId(), dto.getDriverId(), nextStart, nextEnd);

                    BusOperation recurringOp = createSingleOperation(dto, nextStart, nextEnd, organizationId);
                    recurringOp.setParentOperationId(parentId);
                    operations.add(recurringOp);
                } catch (BusinessException e) {
                    log.warn("반복 일정 생성 중 충돌 발생 - {} 주차: {}", week, e.getMessage());
                }
            }
        }

        // 모든 운행 일정 저장
        List<BusOperation> savedOperations = busOperationRepository.saveAll(operations);

        return savedOperations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 일별 운행 일정 조회
     */
    public List<OperationPlanDTO> getOperationPlansByDate(LocalDate date, String organizationId) {
        log.info("일별 운행 일정 조회 - 날짜: {}, 조직: {}", date, organizationId);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<BusOperation> operations = busOperationRepository
                .findByOrganizationIdAndScheduledStartBetween(organizationId, startOfDay, endOfDay);

        return operations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 오늘 운행 일정 조회
     */
    public List<OperationPlanDTO> getTodayOperationPlans(String organizationId) {
        return getOperationPlansByDate(LocalDate.now(), organizationId);
    }

    /**
     * 주별 운행 일정 조회
     */
    public List<OperationPlanDTO> getWeeklyOperationPlans(String organizationId) {
        log.info("주별 운행 일정 조회 - 조직: {}", organizationId);

        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        LocalDateTime start = startOfWeek.atStartOfDay();
        LocalDateTime end = endOfWeek.atTime(LocalTime.MAX);

        List<BusOperation> operations = busOperationRepository
                .findByOrganizationIdAndScheduledStartBetween(organizationId, start, end);

        return operations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 월별 운행 일정 조회
     */
    public List<OperationPlanDTO> getMonthlyOperationPlans(String organizationId) {
        log.info("월별 운행 일정 조회 - 조직: {}", organizationId);

        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth());

        LocalDateTime start = startOfMonth.atStartOfDay();
        LocalDateTime end = endOfMonth.atTime(LocalTime.MAX);

        List<BusOperation> operations = busOperationRepository
                .findByOrganizationIdAndScheduledStartBetween(organizationId, start, end);

        return operations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 특정 운행 일정 상세 조회
     * operationId 또는 id로 조회 가능하도록 수정
     */
    public OperationPlanDTO getOperationPlanDetail(String id, String organizationId) {
        log.info("운행 일정 상세 조회 - ID: {}, 조직: {}", id, organizationId);

        // 먼저 operationId로 조회 시도
        BusOperation operation = busOperationRepository
                .findByOperationIdAndOrganizationId(id, organizationId)
                .orElseGet(() -> {
                    // operationId로 찾지 못한 경우 MongoDB _id로 재시도
                    log.info("operationId로 조회 실패, MongoDB _id로 재시도: {}", id);
                    return busOperationRepository
                            .findByIdAndOrganizationId(id, organizationId)
                            .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + id));
                });

        return convertToDTO(operation);
    }

    /**
     * 운행 일정 수정
     */
    @Transactional
    public OperationPlanDTO updateOperationPlan(OperationPlanDTO dto, String organizationId) {
        log.info("운행 일정 수정 - ID: {}, 조직: {}", dto.getId(), organizationId);

        BusOperation operation = busOperationRepository
                .findByIdAndOrganizationId(dto.getId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + dto.getId()));

        // 완료된 일정은 수정 불가
        if ("COMPLETED".equals(operation.getStatus())) {
            throw new BusinessException("완료된 운행 일정은 수정할 수 없습니다.");
        }

        // 입력값 검증
        validateOperationPlan(dto, organizationId);

        LocalDateTime newStart = LocalDateTime.of(dto.getOperationDate(), dto.getStartTime());
        LocalDateTime newEnd = LocalDateTime.of(dto.getOperationDate(), dto.getEndTime());

        // 자기 자신을 제외한 중복 체크
        List<BusOperation> conflicts = busOperationRepository
                .findConflictingOperations(dto.getBusId(), newStart, newEnd, dto.getDriverId());
        conflicts.removeIf(op -> op.getId().equals(operation.getId()));

        if (!conflicts.isEmpty()) {
            throw new BusinessException("해당 시간에 이미 다른 운행 일정이 있습니다.");
        }

        // 업데이트
        operation.setBusId(new DBRef("Bus", dto.getBusId()));
        operation.setDriverId(new DBRef("Auth", dto.getDriverId()));
        operation.setScheduledStart(newStart);
        operation.setScheduledEnd(newEnd);
        operation.setStatus(dto.getStatus() != null ? dto.getStatus() : operation.getStatus());

        BusOperation updated = busOperationRepository.save(operation);

        return convertToDTO(updated);
    }

    /**
     * 운행 일정 삭제
     */
    @Transactional
    public boolean deleteOperationPlan(String id, String organizationId) {
        log.info("운행 일정 삭제 - ID: {}, 조직: {}", id, organizationId);

        BusOperation operation = busOperationRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + id));

        // 진행 중이거나 완료된 일정은 삭제 불가
        if ("IN_PROGRESS".equals(operation.getStatus()) || "COMPLETED".equals(operation.getStatus())) {
            throw new BusinessException("진행 중이거나 완료된 운행 일정은 삭제할 수 없습니다.");
        }

        // 반복 일정의 경우 연관된 일정도 함께 처리할지 확인 필요
        // 여기서는 단일 삭제만 구현
        busOperationRepository.delete(operation);

        return true;
    }

    /**
     * 단일 운행 일정 생성
     */
    private BusOperation createSingleOperation(OperationPlanDTO dto, LocalDateTime start,
                                               LocalDateTime end, String organizationId) {
        String operationId = UUID.randomUUID().toString();

        return BusOperation.builder()
                .operationId(operationId)
                .busId(new DBRef("Bus", dto.getBusId()))
                .driverId(new DBRef("Auth", dto.getDriverId()))
                .scheduledStart(start)
                .scheduledEnd(end)
                .status("SCHEDULED")
                .organizationId(organizationId)
                .isRecurring(dto.isRecurring())
                .recurringWeeks(dto.getRecurringWeeks())
                .build();
    }

    /**
     * 입력값 검증
     */
    private void validateOperationPlan(OperationPlanDTO dto, String organizationId) {
        // 버스 존재 확인
        Bus bus = busRepository.findById(dto.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + dto.getBusId()));

        if (!bus.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 버스를 배정할 수 없습니다.");
        }

        // 기사 존재 및 권한 확인
        User driver = userRepository.findById(dto.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("기사를 찾을 수 없습니다: " + dto.getDriverId()));

        if (!driver.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 기사를 배정할 수 없습니다.");
        }

        if (!"ROLE_DRIVER".equals(driver.getRoleKey())) {
            throw new BusinessException("버스 기사 권한이 없는 사용자입니다.");
        }

        // 시간 유효성 검증
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BusinessException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }

    /**
     * 일정 중복 체크
     */
    private void checkScheduleConflict(String busId, String driverId,
                                       LocalDateTime start, LocalDateTime end) {
        List<BusOperation> conflicts = busOperationRepository
                .findConflictingOperations(busId, start, end, driverId);

        if (!conflicts.isEmpty()) {
            throw new BusinessException("해당 시간에 이미 다른 운행 일정이 있습니다.");
        }
    }

    /**
     * Entity를 DTO로 변환 - 개선된 버전
     */
    private OperationPlanDTO convertToDTO(BusOperation operation) {
        log.debug("운행 일정 DTO 변환 시작 - ID: {}", operation.getId());

        OperationPlanDTO dto = OperationPlanDTO.builder()
                .id(operation.getId())
                .operationId(operation.getOperationId())
                .operationDate(operation.getScheduledStart().toLocalDate())
                .startTime(operation.getScheduledStart().toLocalTime())
                .endTime(operation.getScheduledEnd().toLocalTime())
                .status(operation.getStatus())
                .isRecurring(operation.isRecurring())
                .recurringWeeks(operation.getRecurringWeeks())
                .organizationId(operation.getOrganizationId())
                .createdAt(operation.getCreatedAt())
                .updatedAt(operation.getUpdatedAt())
                .build();

        // 버스 정보 조회
        if (operation.getBusId() != null) {
            String busId = operation.getBusId().getId().toString();
            dto.setBusId(busId);
            log.debug("버스 ID: {}", busId);

            try {
                Bus bus = busRepository.findById(busId).orElse(null);
                if (bus != null) {
                    dto.setBusNumber(bus.getBusNumber());
                    dto.setBusRealNumber(bus.getBusRealNumber());
                    log.debug("버스 번호: {}, 실제 번호: {}", bus.getBusNumber(), bus.getBusRealNumber());

                    // 라우트 정보 조회
                    if (bus.getRouteId() != null) {
                        String routeId = bus.getRouteId().getId().toString();
                        dto.setRouteId(routeId);
                        log.debug("라우트 ID: {}", routeId);

                        try {
                            Route route = routeRepository.findById(routeId).orElse(null);
                            if (route != null) {
                                dto.setRouteName(route.getRouteName());
                                log.debug("라우트 이름: {}", route.getRouteName());

                                // 출발지/도착지 정보 설정 - 개선된 버전
                                setStartEndLocations(dto, route);
                            } else {
                                log.warn("라우트를 찾을 수 없음: {}", routeId);
                            }
                        } catch (Exception e) {
                            log.error("라우트 정보 조회 실패: {}", e.getMessage(), e);
                        }
                    } else {
                        log.warn("버스에 라우트 ID가 없음: 버스 번호 {}", bus.getBusNumber());
                    }
                } else {
                    log.warn("버스를 찾을 수 없음: {}", busId);
                }
            } catch (Exception e) {
                log.error("버스 정보 조회 실패: {}", e.getMessage(), e);
            }
        }

        // 기사 정보 조회
        if (operation.getDriverId() != null) {
            String driverId = operation.getDriverId().getId().toString();
            dto.setDriverId(driverId);

            userRepository.findById(driverId).ifPresent(driver -> {
                dto.setDriverName(driver.getName());
                log.debug("운전자 이름: {}", driver.getName());
            });
        }

        log.debug("운행 일정 DTO 변환 완료 - startLocation: {}, endLocation: {}",
                dto.getStartLocation(), dto.getEndLocation());

        return dto;
    }

    /**
     * 라우트의 첫 번째와 마지막 정류장을 출발지/도착지로 설정 - 개선된 버전
     */
    private void setStartEndLocations(OperationPlanDTO dto, Route route) {
        if (route.getStations() == null || route.getStations().isEmpty()) {
            log.warn("라우트에 정류장이 없음: {}", route.getId());
            return;
        }

        log.debug("정류장 개수: {}", route.getStations().size());

        try {
            // 출발지 (첫 번째 정류장)
            Route.RouteStation firstRouteStation = route.getStations().get(0);
            if (firstRouteStation != null && firstRouteStation.getStationId() != null) {
                String firstStationId = firstRouteStation.getStationId().getId().toString();
                log.debug("첫 번째 정류장 ID: {}", firstStationId);

                Station firstStation = stationRepository.findById(firstStationId).orElse(null);
                if (firstStation != null) {
                    GeoJsonPoint location = firstStation.getLocation();

                    OperationPlanDTO.LocationInfo startLocation = OperationPlanDTO.LocationInfo.builder()
                            .name(firstStation.getName())
                            .latitude(location != null ? location.getY() : null)
                            .longitude(location != null ? location.getX() : null)
                            .build();

                    dto.setStartLocation(startLocation);
                    log.info("출발지 설정 성공: {} ({}, {})",
                            startLocation.getName(),
                            startLocation.getLatitude(),
                            startLocation.getLongitude());
                } else {
                    log.error("첫 번째 정류장을 찾을 수 없음: {}", firstStationId);
                }
            }

            // 도착지 (마지막 정류장)
            int lastIndex = route.getStations().size() - 1;
            Route.RouteStation lastRouteStation = route.getStations().get(lastIndex);
            if (lastRouteStation != null && lastRouteStation.getStationId() != null) {
                String lastStationId = lastRouteStation.getStationId().getId().toString();
                log.debug("마지막 정류장 ID: {}", lastStationId);

                Station lastStation = stationRepository.findById(lastStationId).orElse(null);
                if (lastStation != null) {
                    GeoJsonPoint location = lastStation.getLocation();

                    OperationPlanDTO.LocationInfo endLocation = OperationPlanDTO.LocationInfo.builder()
                            .name(lastStation.getName())
                            .latitude(location != null ? location.getY() : null)
                            .longitude(location != null ? location.getX() : null)
                            .build();

                    dto.setEndLocation(endLocation);
                    log.info("도착지 설정 성공: {} ({}, {})",
                            endLocation.getName(),
                            endLocation.getLatitude(),
                            endLocation.getLongitude());
                } else {
                    log.error("마지막 정류장을 찾을 수 없음: {}", lastStationId);
                }
            }
        } catch (Exception e) {
            log.error("출발지/도착지 설정 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 특정 운전자의 오늘 운행 일정 조회
     */
    public List<OperationPlanDTO> getDriverTodayOperationPlans(String driverEmail, String organizationId) {
        log.info("운전자 오늘 운행 일정 조회 - 운전자 이메일: {}, 조직: {}", driverEmail, organizationId);

        // 이메일로 운전자 User 조회
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

        // 운전자 권한 확인
        if (!driver.getRoleKey().equals("ROLE_DRIVER")) {
            throw new BusinessException("운전자 권한이 없는 사용자입니다.");
        }

        // 오늘 날짜의 시작과 끝
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        // 해당 운전자의 오늘 운행 일정 조회
        List<BusOperation> operations = busOperationRepository
                .findByDriverIdAndOrganizationId(driver.getId(), organizationId)
                .stream()
                .filter(op -> !op.getScheduledStart().isBefore(startOfDay)
                        && !op.getScheduledStart().isAfter(endOfDay))
                .collect(Collectors.toList());

        return operations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 특정 운전자의 특정 날짜 운행 일정 조회
     */
    public List<OperationPlanDTO> getDriverOperationPlansByDate(String driverEmail, LocalDate date, String organizationId) {
        log.info("운전자 특정 날짜 운행 일정 조회 - 날짜: {}, 운전자 이메일: {}, 조직: {}", date, driverEmail, organizationId);

        // 이메일로 운전자 User 조회
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

        // 운전자 권한 확인
        if (!driver.getRoleKey().equals("ROLE_DRIVER")) {
            throw new BusinessException("운전자 권한이 없는 사용자입니다.");
        }

        // 특정 날짜의 시작과 끝
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 해당 운전자의 특정 날짜 운행 일정 조회
        List<BusOperation> operations = busOperationRepository
                .findByDriverIdAndOrganizationId(driver.getId(), organizationId)
                .stream()
                .filter(op -> !op.getScheduledStart().isBefore(startOfDay)
                        && !op.getScheduledStart().isAfter(endOfDay))
                .collect(Collectors.toList());

        return operations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 특정 운전자의 월간 운행 일정 조회
     */
    public List<OperationPlanDTO> getDriverMonthlyOperationPlans(String driverEmail, int year, int month, String organizationId) {
        log.info("운전자 월간 운행 일정 조회 - 년월: {}-{}, 운전자 이메일: {}, 조직: {}", year, month, driverEmail, organizationId);

        // 이메일로 운전자 User 조회
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new ResourceNotFoundException("운전자를 찾을 수 없습니다: " + driverEmail));

        // 운전자 권한 확인
        if (!driver.getRoleKey().equals("ROLE_DRIVER")) {
            throw new BusinessException("운전자 권한이 없는 사용자입니다.");
        }

        // 해당 월의 시작과 끝 날짜 계산
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.with(TemporalAdjusters.lastDayOfMonth());

        LocalDateTime start = startOfMonth.atStartOfDay();
        LocalDateTime end = endOfMonth.atTime(LocalTime.MAX);

        // 해당 운전자의 월간 운행 일정 조회
        List<BusOperation> operations = busOperationRepository
                .findByDriverIdAndOrganizationId(driver.getId(), organizationId)
                .stream()
                .filter(op -> !op.getScheduledStart().isBefore(start)
                        && !op.getScheduledStart().isAfter(end))
                .collect(Collectors.toList());

        log.info("운전자 월간 운행 일정 조회 완료 - 총 {}개 일정", operations.size());

        return operations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
}