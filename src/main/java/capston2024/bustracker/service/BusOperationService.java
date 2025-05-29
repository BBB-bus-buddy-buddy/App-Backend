// src/main/java/capston2024/bustracker/service/BusOperationService.java
package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.OperationPlanDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusOperationRepository;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.UserRepository;
import com.mongodb.DBRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BusOperationService {

    private final BusOperationRepository busOperationRepository;
    private final BusRepository busRepository;
    private final UserRepository userRepository;
    private final RouteService routeService;

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
     */
    public OperationPlanDTO getOperationPlanDetail(String id, String organizationId) {
        log.info("운행 일정 상세 조회 - ID: {}, 조직: {}", id, organizationId);

        BusOperation operation = busOperationRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행 일정을 찾을 수 없습니다: " + id));

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
        Bus bus = busRepository.findByBusNumberAndOrganizationId(dto.getBusNumber(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + dto.getBusId()));

        // 기사 존재 및 권한 확인
        User driver = userRepository.findById(dto.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("기사를 찾을 수 없습니다: " + dto.getDriverId()));

        if (!driver.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 기사를 배정할 수 없습니다.");
        }

        if (!"DRIVER".equals(driver.getRoleKey())) {
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
     * Entity를 DTO로 변환
     */
    private OperationPlanDTO convertToDTO(BusOperation operation) {
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

            busRepository.findById(busId).ifPresent(bus -> {
                dto.setBusNumber(bus.getBusNumber());
                dto.setBusRealNumber(bus.getBusRealNumber());

                // 라우트 정보 조회
                if (bus.getRouteId() != null) {
                    String routeId = bus.getRouteId().getId().toString();
                    dto.setRouteId(routeId);
                    try {
                        dto.setRouteName(routeService.getRouteById(routeId, operation.getOrganizationId()).getRouteName());
                    } catch (Exception e) {
                        log.warn("라우트 정보 조회 실패: {}", e.getMessage());
                    }
                }
            });
        }

        // 기사 정보 조회
        if (operation.getDriverId() != null) {
            String driverId = operation.getDriverId().getId().toString();
            dto.setDriverId(driverId);

            userRepository.findById(driverId).ifPresent(driver -> {
                dto.setDriverName(driver.getName());
            });
        }

        return dto;
    }
}