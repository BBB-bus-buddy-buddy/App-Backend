package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.busEtc.BusBoardingDTO;
import capston2024.bustracker.config.dto.busEtc.DriverLocationUpdateDTO;
import capston2024.bustracker.config.dto.busEtc.PassengerLocationDTO;
import capston2024.bustracker.config.dto.realtime.BoardingDetectionResultDTO;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.repository.BusOperationRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class PassengerLocationService {

    private final BusOperationRepository busOperationRepository;
    private final BusOperationService busOperationService;
    private final RealtimeLocationService realtimeLocationService;

    // 승객 상태 관리 (userId -> 상태)
    private final Map<String, PassengerState> passengerStates = new ConcurrentHashMap<>();

    // 거리 임계값 (미터)
    private static final double BOARDING_DISTANCE_THRESHOLD = 25.0;  // 25m 이내 탑승
    private static final double ALIGHTING_DISTANCE_THRESHOLD = 50.0; // 50m 이상 하차
    private static final int CONSECUTIVE_DETECTION_THRESHOLD = 3;    // 연속 감지 3회

    /**
     * 승객 위치 기반 자동 탑승 감지 처리
     */
    public BoardingDetectionResultDTO processPassengerLocation(PassengerLocationDTO locationDTO) {
        String userId = locationDTO.getUserId();
        String organizationId = locationDTO.getOrganizationId();

        // 승객 상태 조회 또는 생성
        PassengerState state = passengerStates.computeIfAbsent(userId,
                k -> new PassengerState(userId, organizationId));

        // 위치 업데이트
        state.updateLocation(locationDTO.getLatitude(), locationDTO.getLongitude(), locationDTO.getTimestamp());

        // 해당 조직의 진행 중인 운행들 조회
        List<BusOperation> activeOperations = busOperationRepository.findByOrganizationIdAndStatus(
                organizationId, BusOperation.OperationStatus.IN_PROGRESS);

        if (state.isOnBus()) {
            // 이미 버스에 탑승 중 → 하차 감지
            return detectAlighting(state, activeOperations);
        } else {
            // 버스에 탑승하지 않음 → 탑승 감지
            return detectBoarding(state, activeOperations);
        }
    }

    /**
     * 수동 탑승/하차 처리
     */
    public boolean processManualBoarding(BusBoardingDTO boardingDTO) {
        try {
            boolean success = busOperationService.processBusBoarding(boardingDTO);

            if (success) {
                // 수동 처리 시 승객 상태도 업데이트
                PassengerState state = passengerStates.get(boardingDTO.getUserId());
                if (state != null) {
                    if (boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD) {
                        state.setOnBus(true);
                        state.setCurrentOperationId(findOperationIdByBusNumber(boardingDTO.getBusNumber(), boardingDTO.getOrganizationId()));
                    } else {
                        state.setOnBus(false);
                        state.setCurrentOperationId(null);
                    }
                }
            }

            return success;
        } catch (Exception e) {
            log.error("수동 탑승 처리 중 오류", e);
            return false;
        }
    }

    /**
     * 탑승 감지 (25m 이내 접근)
     */
    private BoardingDetectionResultDTO detectBoarding(PassengerState state, List<BusOperation> operations) {
        OperationDistance closest = findClosestOperation(state, operations);

        if (closest != null && closest.distance <= BOARDING_DISTANCE_THRESHOLD) {
            // 탑승 감지 카운트 증가
            state.incrementBoardingDetectionCount(closest.operation.getOperationId());

            log.debug("승객 {} 탑승 감지 중: 운행={}, 거리={}m, 카운트={}",
                    state.getUserId(), closest.operation.getOperationId(),
                    closest.distance, state.getBoardingDetectionCount());

            // 연속 감지 임계값 도달 시 탑승 처리
            if (state.getBoardingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                return processAutoBoarding(state, closest.operation, closest.distance);
            }
        } else {
            // 가까운 버스가 없으면 카운트 리셋
            state.resetBoardingDetectionCount();
        }

        return null;
    }

    /**
     * 하차 감지 (50m 이상 이탈)
     */
    private BoardingDetectionResultDTO detectAlighting(PassengerState state, List<BusOperation> operations) {
        // 현재 탑승 중인 운행 찾기
        BusOperation currentOperation = operations.stream()
                .filter(op -> op.getOperationId().equals(state.getCurrentOperationId()))
                .findFirst()
                .orElse(null);

        if (currentOperation == null) {
            log.warn("승객 {}의 탑승 운행 {}을 찾을 수 없음",
                    state.getUserId(), state.getCurrentOperationId());
            // 상태 초기화
            state.setOnBus(false);
            state.setCurrentOperationId(null);
            return null;
        }

        // 해당 운행의 실시간 위치 조회
        DriverLocationUpdateDTO busLocation = realtimeLocationService.getCurrentLocation(currentOperation.getOperationId());
        if (busLocation == null) {
            log.debug("운행 {}의 실시간 위치 정보 없음", currentOperation.getOperationId());
            return null;
        }

        // 버스와 승객 간 거리 계산
        double distance = calculateDistance(
                state.getLatitude(), state.getLongitude(),
                busLocation.getLatitude(), busLocation.getLongitude()
        );

        if (distance > ALIGHTING_DISTANCE_THRESHOLD) {
            // 하차 감지 카운트 증가
            state.incrementAlightingDetectionCount();

            log.debug("승객 {} 하차 감지 중: 운행={}, 거리={}m, 카운트={}",
                    state.getUserId(), currentOperation.getOperationId(),
                    distance, state.getAlightingDetectionCount());

            // 연속 감지 임계값 도달 시 하차 처리
            if (state.getAlightingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                return processAutoAlighting(state, currentOperation, distance);
            }
        } else {
            // 버스와 가까우면 카운트 리셋
            state.resetAlightingDetectionCount();
        }

        return null;
    }

    /**
     * 자동 탑승 처리
     */
    private BoardingDetectionResultDTO processAutoBoarding(PassengerState state, BusOperation operation, double distance) {
        log.info("승객 {} 자동 탑승 처리: 운행={}, 거리={}m",
                state.getUserId(), operation.getOperationId(), distance);

        // 탑승 DTO 생성
        BusBoardingDTO boardingDTO = new BusBoardingDTO();
        boardingDTO.setBusNumber(getBusNumberFromOperation(operation));
        boardingDTO.setOrganizationId(operation.getOrganizationId());
        boardingDTO.setUserId(state.getUserId());
        boardingDTO.setAction(BusBoardingDTO.BoardingAction.BOARD);
        boardingDTO.setTimestamp(System.currentTimeMillis());

        // 실제 탑승 처리
        boolean success = busOperationService.processBusBoarding(boardingDTO);

        if (success) {
            // 탑승 상태 업데이트
            state.setOnBus(true);
            state.setCurrentOperationId(operation.getOperationId());
            state.resetBoardingDetectionCount();
            state.resetAlightingDetectionCount();
        }

        return BoardingDetectionResultDTO.builder()
                .userId(state.getUserId())
                .operationId(operation.getOperationId())
                .busNumber(getBusNumberFromOperation(operation))
                .action(BusBoardingDTO.BoardingAction.BOARD)
                .autoDetected(true)
                .detectionDistance(distance)
                .timestamp(System.currentTimeMillis())
                .successful(success)
                .message(success ? "자동 탑승 완료" : "탑승 처리 실패")
                .build();
    }

    /**
     * 자동 하차 처리
     */
    private BoardingDetectionResultDTO processAutoAlighting(PassengerState state, BusOperation operation, double distance) {
        log.info("승객 {} 자동 하차 처리: 운행={}, 거리={}m",
                state.getUserId(), operation.getOperationId(), distance);

        // 하차 DTO 생성
        BusBoardingDTO boardingDTO = new BusBoardingDTO();
        boardingDTO.setBusNumber(getBusNumberFromOperation(operation));
        boardingDTO.setOrganizationId(operation.getOrganizationId());
        boardingDTO.setUserId(state.getUserId());
        boardingDTO.setAction(BusBoardingDTO.BoardingAction.ALIGHT);
        boardingDTO.setTimestamp(System.currentTimeMillis());

        // 실제 하차 처리
        boolean success = busOperationService.processBusBoarding(boardingDTO);

        if (success) {
            // 하차 상태 업데이트
            state.setOnBus(false);
            state.setCurrentOperationId(null);
            state.resetBoardingDetectionCount();
            state.resetAlightingDetectionCount();
        }

        return BoardingDetectionResultDTO.builder()
                .userId(state.getUserId())
                .operationId(operation.getOperationId())
                .busNumber(getBusNumberFromOperation(operation))
                .action(BusBoardingDTO.BoardingAction.ALIGHT)
                .autoDetected(true)
                .detectionDistance(distance)
                .timestamp(System.currentTimeMillis())
                .successful(success)
                .message(success ? "자동 하차 완료" : "하차 처리 실패")
                .build();
    }

    /**
     * 가장 가까운 운행 찾기
     */
    private OperationDistance findClosestOperation(PassengerState state, List<BusOperation> operations) {
        OperationDistance closest = null;
        double minDistance = Double.MAX_VALUE;

        for (BusOperation operation : operations) {
            // 실시간 위치 조회
            DriverLocationUpdateDTO busLocation = realtimeLocationService.getCurrentLocation(operation.getOperationId());
            if (busLocation == null) {
                continue;
            }

            double distance = calculateDistance(
                    state.getLatitude(), state.getLongitude(),
                    busLocation.getLatitude(), busLocation.getLongitude()
            );

            if (distance < minDistance) {
                minDistance = distance;
                closest = new OperationDistance(operation, distance);
            }
        }

        return closest;
    }

    /**
     * 두 위치 사이의 거리 계산 (Haversine 공식)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // 지구의 반지름 (미터)

        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    }

    /**
     * 버스 번호로 운행 ID 찾기
     */
    private String findOperationIdByBusNumber(String busNumber, String organizationId) {
        try {
            BusOperation operation = busOperationService.getCurrentOperationByBusNumber(busNumber, organizationId);
            return operation != null ? operation.getOperationId() : null;
        } catch (Exception e) {
            log.error("운행 ID 조회 실패: 버스={}", busNumber, e);
            return null;
        }
    }

    /**
     * BusOperation에서 버스 번호 조회
     */
    private String getBusNumberFromOperation(BusOperation operation) {
        // BusOperationService를 통해 조회하거나, 직접 Bus 엔티티 조회
        try {
            return busOperationService.getBusNumberFromOperation(operation);
        } catch (Exception e) {
            log.warn("버스 번호 조회 실패: {}", operation.getOperationId(), e);
            return "알 수 없음";
        }
    }

    /**
     * 승객 상태 관리 내부 클래스
     */
    @Getter @Setter
    private static class PassengerState {
        private final String userId;
        private final String organizationId;
        private double latitude;
        private double longitude;
        private long timestamp;
        private boolean onBus;                    // 탑승 상태
        private String currentOperationId;        // 현재 탑승 중인 운행 ID
        private int boardingDetectionCount;       // 탑승 감지 카운트
        private String pendingOperationId;        // 탑승 감지 중인 운행 ID
        private int alightingDetectionCount;      // 하차 감지 카운트

        PassengerState(String userId, String organizationId) {
            this.userId = userId;
            this.organizationId = organizationId;
            this.onBus = false;
            this.currentOperationId = null;
            this.boardingDetectionCount = 0;
            this.pendingOperationId = null;
            this.alightingDetectionCount = 0;
        }

        void updateLocation(double latitude, double longitude, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }

        void incrementBoardingDetectionCount(String operationId) {
            if (pendingOperationId == null || !pendingOperationId.equals(operationId)) {
                pendingOperationId = operationId;
                boardingDetectionCount = 1;
            } else {
                boardingDetectionCount++;
            }
        }

        void resetBoardingDetectionCount() {
            boardingDetectionCount = 0;
            pendingOperationId = null;
        }

        void incrementAlightingDetectionCount() {
            alightingDetectionCount++;
        }

        void resetAlightingDetectionCount() {
            alightingDetectionCount = 0;
        }
    }

    /**
     * 운행과 거리 정보
     */
    private static class OperationDistance {
        final BusOperation operation;
        final double distance;

        OperationDistance(BusOperation operation, double distance) {
            this.operation = operation;
            this.distance = distance;
        }
    }
}