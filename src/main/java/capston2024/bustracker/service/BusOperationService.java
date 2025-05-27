package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.busEtc.BusBoardingDTO;
import capston2024.bustracker.config.dto.busEtc.SeatInfoDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationCreateDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationStatusUpdateDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.domain.Route;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.BusOperationRepository;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.RouteRepository;
import capston2024.bustracker.repository.UserRepository;
import com.mongodb.DBRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final RouteRepository routeRepository;

    /**
     * 새로운 버스 운행 생성
     */
    @Transactional
    public BusOperationDTO createBusOperation(String organizationId, BusOperationCreateDTO createDTO) {
        log.info("새로운 버스 운행 생성 - 조직: {}, 버스: {}, 기사: {}",
                organizationId, createDTO.getBusId(), createDTO.getDriverId());

        // 버스 존재 및 조직 확인
        Bus bus = busRepository.findById(createDTO.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + createDTO.getBusId()));

        if (!bus.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 버스로는 운행을 생성할 수 없습니다.");
        }

        // 기사 존재 및 조직 확인
        User driver = userRepository.findById(createDTO.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("기사를 찾을 수 없습니다: " + createDTO.getDriverId()));

        if (!driver.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 기사로는 운행을 생성할 수 없습니다.");
        }

        // 라우트 존재 및 조직 확인
        Route route = null;
        if (createDTO.getRouteId() != null) {
            route = routeRepository.findById(createDTO.getRouteId())
                    .orElseThrow(() -> new ResourceNotFoundException("라우트를 찾을 수 없습니다: " + createDTO.getRouteId()));

            if (!route.getOrganizationId().equals(organizationId)) {
                throw new BusinessException("다른 조직의 라우트로는 운행을 생성할 수 없습니다.");
            }
        }

        // 운행 ID 생성
        String operationId = generateOperationId(organizationId);

        // 버스 운행 엔티티 생성
        BusOperation busOperation = BusOperation.builder()
                .operationId(operationId)
                .busId(new DBRef("Bus", bus.getId()))
                .driverId(new DBRef("Auth", driver.getId()))
                .scheduledStart(createDTO.getScheduledStart())
                .scheduledEnd(createDTO.getScheduledEnd())
                .status(BusOperation.OperationStatus.SCHEDULED)
                .organizationId(organizationId)
                .totalPassengers(0)
                .totalStopsCompleted(0)
                .routeId(route != null ? new DBRef("routes", route.getId()) : null)
                .build();

        BusOperation savedOperation = busOperationRepository.save(busOperation);
        log.info("버스 운행 생성 완료 - 운행 ID: {}", operationId);

        return convertToDTO(savedOperation);
    }

    /**
     * 운행 상태 업데이트
     */
    @Transactional
    public BusOperationDTO updateOperationStatus(String organizationId, BusOperationStatusUpdateDTO updateDTO) {
        log.info("운행 상태 업데이트 - 운행 ID: {}, 상태: {}",
                updateDTO.getOperationId(), updateDTO.getStatus());

        BusOperation operation = busOperationRepository.findByOperationId(updateDTO.getOperationId())
                .orElseThrow(() -> new ResourceNotFoundException("운행을 찾을 수 없습니다: " + updateDTO.getOperationId()));

        if (!operation.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 운행은 수정할 수 없습니다.");
        }

        // 상태별 처리
        switch (updateDTO.getStatus()) {
            case IN_PROGRESS:
                if (operation.getActualStart() == null) {
                    operation.setActualStart(LocalDateTime.now());
                }
                break;
            case COMPLETED:
                if (operation.getActualEnd() == null) {
                    operation.setActualEnd(LocalDateTime.now());
                }
                break;
        }

        operation.setStatus(updateDTO.getStatus());

        if (updateDTO.getCurrentPassengers() != null) {
            operation.setTotalPassengers(updateDTO.getCurrentPassengers());
        }

        if (updateDTO.getCurrentStopsCompleted() != null) {
            operation.setTotalStopsCompleted(updateDTO.getCurrentStopsCompleted());
        }

        BusOperation updatedOperation = busOperationRepository.save(operation);
        log.info("운행 상태 업데이트 완료 - 운행 ID: {}", updateDTO.getOperationId());

        return convertToDTO(updatedOperation);
    }

    /**
     * 조직의 모든 운행 조회
     */
    public List<BusOperationDTO> getAllOperationsByOrganization(String organizationId) {
        log.info("조직 {}의 모든 운행 조회", organizationId);
        List<BusOperation> operations = busOperationRepository.findByOrganizationId(organizationId);
        return operations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 운행 상태별 조회
     */
    public List<BusOperationDTO> getOperationsByStatus(String organizationId, BusOperation.OperationStatus status) {
        log.info("조직 {}의 {} 상태 운행 조회", organizationId, status);
        List<BusOperation> operations = busOperationRepository.findByOrganizationIdAndStatus(organizationId, status);
        return operations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 기사의 오늘 운행 스케줄 조회
     */
    public List<BusOperationDTO> getTodayOperationsByDriver(String driverId) {
        log.info("기사 {}의 오늘 운행 스케줄 조회", driverId);
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<BusOperation> operations = busOperationRepository.findTodayOperationsByDriverId(
                driverId, startOfDay, endOfDay);

        return operations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 운행 상세 조회
     */
    public BusOperationDTO getOperationById(String organizationId, String operationId) {
        log.info("운행 상세 조회 - 운행 ID: {}", operationId);
        BusOperation operation = busOperationRepository.findByOperationId(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행을 찾을 수 없습니다: " + operationId));

        if (!operation.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 운행은 조회할 수 없습니다.");
        }

        return convertToDTO(operation);
    }

    /**
     * 운행 ID 생성
     */
    private String generateOperationId(String organizationId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("OP%s%s%s", organizationId.substring(0, Math.min(4, organizationId.length())), timestamp, uuid);
    }

    /**
     * BusOperation 엔티티를 DTO로 변환
     */
    BusOperationDTO convertToDTO(BusOperation operation) {
        // 버스 정보 조회
        String busNumber = "알 수 없음";
        String busRealNumber = null;
        if (operation.getBusId() != null) {
            try {
                Bus bus = busRepository.findById(operation.getBusId().getId().toString()).orElse(null);
                if (bus != null) {
                    busNumber = bus.getBusNumber();
                    busRealNumber = bus.getBusRealNumber();
                }
            } catch (Exception e) {
                log.warn("버스 정보 조회 실패: {}", operation.getBusId().getId(), e);
            }
        }

        // 기사 정보 조회
        String driverName = "알 수 없음";
        String driverEmail = null;
        if (operation.getDriverId() != null) {
            try {
                User driver = userRepository.findById(operation.getDriverId().getId().toString()).orElse(null);
                if (driver != null) {
                    driverName = driver.getName();
                    driverEmail = driver.getEmail();
                }
            } catch (Exception e) {
                log.warn("기사 정보 조회 실패: {}", operation.getDriverId().getId(), e);
            }
        }

        // 라우트 정보 조회
        String routeName = null;
        if (operation.getRouteId() != null) {
            try {
                Route route = routeRepository.findById(operation.getRouteId().getId().toString()).orElse(null);
                if (route != null) {
                    routeName = route.getRouteName();
                }
            } catch (Exception e) {
                log.warn("라우트 정보 조회 실패: {}", operation.getRouteId().getId(), e);
            }
        }

        return BusOperationDTO.builder()
                .id(operation.getId())
                .operationId(operation.getOperationId())
                .busNumber(busNumber)
                .busRealNumber(busRealNumber)
                .driverName(driverName)
                .driverEmail(driverEmail)
                .scheduledStart(operation.getScheduledStart())
                .scheduledEnd(operation.getScheduledEnd())
                .actualStart(operation.getActualStart())
                .actualEnd(operation.getActualEnd())
                .status(operation.getStatus())
                .organizationId(operation.getOrganizationId())
                .totalPassengers(operation.getTotalPassengers())
                .totalStopsCompleted(operation.getTotalStopsCompleted())
                .routeName(routeName)
                .createdAt(operation.getCreatedAt())
                .updatedAt(operation.getUpdatedAt())
                .build();
    }

    /**
     * 승객 탑승/하차 처리 (기존 BusService.processBusBoarding를 이관)
     */
    @Transactional
    public boolean processBusBoarding(BusBoardingDTO boardingDTO) {
        log.info("승객 탑승/하차 처리: 버스={}, 사용자={}, 액션={}",
                boardingDTO.getBusNumber(), boardingDTO.getUserId(), boardingDTO.getAction());

        try {
            // 1. 해당 버스의 현재 진행 중인 운행 찾기
            BusOperation currentOperation = getCurrentOperationByBusNumber(
                    boardingDTO.getBusNumber(), boardingDTO.getOrganizationId());

            if (currentOperation == null) {
                log.warn("버스 {}에 진행 중인 운행이 없습니다", boardingDTO.getBusNumber());
                return false;
            }

            // 2. 탑승/하차 처리
            if (boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD) {
                // 탑승 처리
                return processBoarding(currentOperation);
            } else {
                // 하차 처리
                return processAlighting(currentOperation);
            }

        } catch (Exception e) {
            log.error("탑승/하차 처리 중 오류 발생", e);
            return false;
        }
    }

    /**
     * 버스 번호로 현재 진행 중인 운행 조회
     */
    BusOperation getCurrentOperationByBusNumber(String busNumber, String organizationId) {
        // 1. 버스 조회
        Bus bus = busRepository.findByBusNumberAndOrganizationId(busNumber, organizationId)
                .orElse(null);

        if (bus == null) {
            return null;
        }

        // 2. 해당 버스의 진행 중인 운행 조회
        List<BusOperation> activeOperations = busOperationRepository.findByBusIdAndStatusIn(
                bus.getId(),
                List.of(BusOperation.OperationStatus.IN_PROGRESS, BusOperation.OperationStatus.SCHEDULED)
        );

        // 3. IN_PROGRESS 우선, 없으면 SCHEDULED
        return activeOperations.stream()
                .filter(op -> op.getStatus() == BusOperation.OperationStatus.IN_PROGRESS)
                .findFirst()
                .orElse(activeOperations.stream().findFirst().orElse(null));
    }

    /**
     * 탑승 처리
     */
    private boolean processBoarding(BusOperation operation) {
        // 버스 정보 조회해서 총 좌석 수 확인
        Bus bus = busRepository.findById(operation.getBusId().getId().toString()).orElse(null);
        if (bus == null) {
            log.error("버스 정보를 찾을 수 없습니다: {}", operation.getBusId().getId());
            return false;
        }

        int currentPassengers = operation.getTotalPassengers() != null ? operation.getTotalPassengers() : 0;

        if (currentPassengers >= bus.getTotalSeats()) {
            log.warn("버스 {} 탑승 실패: 좌석이 모두 찼습니다", operation.getOperationId());
            return false;
        }

        // 승객 수 증가
        operation.setTotalPassengers(currentPassengers + 1);
        busOperationRepository.save(operation);

        log.info("탑승 완료: 운행={}, 현재승객={}", operation.getOperationId(), currentPassengers + 1);
        return true;
    }

    /**
     * 하차 처리
     */
    private boolean processAlighting(BusOperation operation) {
        int currentPassengers = operation.getTotalPassengers() != null ? operation.getTotalPassengers() : 0;

        if (currentPassengers <= 0) {
            log.warn("버스 {} 하차 실패: 탑승한 승객이 없습니다", operation.getOperationId());
            return false;
        }

        // 승객 수 감소
        operation.setTotalPassengers(currentPassengers - 1);
        busOperationRepository.save(operation);

        log.info("하차 완료: 운행={}, 현재승객={}", operation.getOperationId(), currentPassengers - 1);
        return true;
    }

    /**
     * 운행의 현재 좌석 정보 조회
     */
    public SeatInfoDTO getOperationSeatInfo(String operationId, String organizationId) {
        BusOperation operation = busOperationRepository.findByOperationId(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("운행을 찾을 수 없습니다: " + operationId));

        if (!operation.getOrganizationId().equals(organizationId)) {
            throw new BusinessException("다른 조직의 운행은 조회할 수 없습니다.");
        }

        // 버스 정보 조회
        Bus bus = busRepository.findById(operation.getBusId().getId().toString())
                .orElseThrow(() -> new ResourceNotFoundException("버스 정보를 찾을 수 없습니다"));

        int currentPassengers = operation.getTotalPassengers() != null ? operation.getTotalPassengers() : 0;

        return new SeatInfoDTO(
                operationId,
                getBusNumberFromOperation(operation),
                bus.getTotalSeats(),
                currentPassengers,
                bus.getTotalSeats() - currentPassengers,
                operation.getUpdatedAt() != null ?
                        operation.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                        System.currentTimeMillis()
        );
    }

    /**
     * BusOperation에서 버스 번호 조회
     */
    String getBusNumberFromOperation(BusOperation operation) {
        try {
            Bus bus = busRepository.findById(operation.getBusId().getId().toString()).orElse(null);
            return bus != null ? bus.getBusNumber() : "알 수 없음";
        } catch (Exception e) {
            log.warn("버스 번호 조회 실패: {}", operation.getBusId().getId(), e);
            return "알 수 없음";
        }
    }
}