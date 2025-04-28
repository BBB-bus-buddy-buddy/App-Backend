package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.BusBoardingDTO;
import capston2024.bustracker.config.dto.PassengerLocationDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.repository.BusRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class PassengerLocationService {

    private final BusRepository busRepository;
    private final BusService busService;

    // 승객별 상태 관리 (userId -> 상태 맵)
    private final Map<String, PassengerState> passengerStates = new ConcurrentHashMap<>();

    // 자동 탑승 감지를 위한 거리 임계값 (미터 단위)
    private static final double AUTO_BOARDING_DISTANCE_THRESHOLD = 25.0;

    // 연속 감지를 위한 최소 카운트
    private static final int CONSECUTIVE_DETECTION_THRESHOLD = 3;

    // 자동 하차 감지를 위한 거리 임계값 (미터 단위)
    private static final double AUTO_ALIGHTING_DISTANCE_THRESHOLD = 50.0;

    /**
     * 승객 위치 정보 처리
     * @param locationDTO 승객 위치 정보
     * @return 자동 탑승/하차 감지 여부
     */
    public boolean processPassengerLocation(PassengerLocationDTO locationDTO) {
        String userId = locationDTO.getUserId();
        String organizationId = locationDTO.getOrganizationId();

        // 승객 상태 조회 또는 생성
        PassengerState state = passengerStates.computeIfAbsent(userId,
                k -> new PassengerState(userId, organizationId));

        // 위치 업데이트
        state.updateLocation(locationDTO.getLatitude(), locationDTO.getLongitude(), locationDTO.getTimestamp());

        // 조직의 모든 버스 조회
        List<Bus> buses = busRepository.findByOrganizationId(organizationId);

        // 승객 상태에 따른 처리
        if (state.isOnBus()) {
            // 이미 버스에 탑승 중인 경우 - 하차 감지 처리
            return detectAlighting(state, buses);
        } else {
            // 버스에 탑승하지 않은 경우 - 탑승 감지 처리
            return detectBoarding(state, buses);
        }
    }

    /**
     * 탑승 감지 처리
     */
    private boolean detectBoarding(PassengerState state, List<Bus> buses) {
        // 가장 가까운 버스와 거리 찾기
        BusDistance closestBus = findClosestBus(state, buses);

        if (closestBus != null && closestBus.distance <= AUTO_BOARDING_DISTANCE_THRESHOLD) {
            // 탑승 감지 카운트 증가
            state.incrementBoardingDetectionCount(closestBus.bus.getBusNumber());

            // 연속 감지 횟수가 임계값을 초과하면 탑승 처리
            if (state.getBoardingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                log.info("승객 {} 자동 탑승 감지: 버스={}, 거리={}m",
                        state.getUserId(), closestBus.bus.getBusNumber(), closestBus.distance);

                // 탑승 처리
                processBoarding(state, closestBus.bus);
                return true;
            }
        } else {
            // 가까운 버스가 없으면 카운트 리셋
            state.resetBoardingDetectionCount();
        }

        return false;
    }

    /**
     * 하차 감지 처리
     */
    private boolean detectAlighting(PassengerState state, List<Bus> buses) {
        // 현재 탑승 중인 버스 찾기
        Bus onBus = null;
        for (Bus bus : buses) {
            if (bus.getBusNumber().equals(state.getCurrentBusNumber())) {
                onBus = bus;
                break;
            }
        }

        if (onBus == null) {
            log.warn("승객 {}가 탑승 중인 버스 {}를 찾을 수 없습니다",
                    state.getUserId(), state.getCurrentBusNumber());
            return false;
        }

        // 버스와의 거리 계산
        double distance = calculateDistance(
                state.getLatitude(), state.getLongitude(),
                onBus.getLocation().getY(), onBus.getLocation().getX()
        );

        // 거리가 임계값을 초과하면 하차 감지 카운트 증가
        if (distance > AUTO_ALIGHTING_DISTANCE_THRESHOLD) {
            state.incrementAlightingDetectionCount();

            // 연속 감지 횟수가 임계값을 초과하면 하차 처리
            if (state.getAlightingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                log.info("승객 {} 자동 하차 감지: 버스={}, 거리={}m",
                        state.getUserId(), onBus.getBusNumber(), distance);

                // 하차 처리
                processAlighting(state, onBus);
                return true;
            }
        } else {
            // 버스와 거리가 가까우면 카운트 리셋
            state.resetAlightingDetectionCount();
        }

        return false;
    }

    /**
     * 가장 가까운 버스와 거리 찾기
     */
    private BusDistance findClosestBus(PassengerState state, List<Bus> buses) {
        BusDistance closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Bus bus : buses) {
            if (bus.getLocation() == null) continue;

            double distance = calculateDistance(
                    state.getLatitude(), state.getLongitude(),
                    bus.getLocation().getY(), bus.getLocation().getX()
            );

            if (distance < minDistance) {
                minDistance = distance;
                closest = new BusDistance(bus, distance);
            }
        }

        return closest;
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

    /**
     * 자동 탑승 처리
     */
    private void processBoarding(PassengerState state, Bus bus) {
        BusBoardingDTO boardingDTO = new BusBoardingDTO();
        boardingDTO.setBusNumber(bus.getBusNumber());
        boardingDTO.setOrganizationId(state.getOrganizationId());
        boardingDTO.setUserId(state.getUserId());
        boardingDTO.setAction(BusBoardingDTO.BoardingAction.BOARD);
        boardingDTO.setTimestamp(System.currentTimeMillis());

        boolean success = busService.processBusBoarding(boardingDTO);

        if (success) {
            // 탑승 상태 업데이트
            state.setOnBus(true);
            state.setCurrentBusNumber(bus.getBusNumber());
            state.resetBoardingDetectionCount();
            state.resetAlightingDetectionCount();

            log.info("승객 {} 자동 탑승 처리 완료: 버스={}", state.getUserId(), bus.getBusNumber());
        } else {
            log.warn("승객 {} 자동 탑승 처리 실패: 버스={}", state.getUserId(), bus.getBusNumber());
        }
    }

    /**
     * 자동 하차 처리
     */
    private void processAlighting(PassengerState state, Bus bus) {
        BusBoardingDTO boardingDTO = new BusBoardingDTO();
        boardingDTO.setBusNumber(bus.getBusNumber());
        boardingDTO.setOrganizationId(state.getOrganizationId());
        boardingDTO.setUserId(state.getUserId());
        boardingDTO.setAction(BusBoardingDTO.BoardingAction.ALIGHT);
        boardingDTO.setTimestamp(System.currentTimeMillis());

        boolean success = busService.processBusBoarding(boardingDTO);

        if (success) {
            // 하차 상태 업데이트
            state.setOnBus(false);
            state.setCurrentBusNumber(null);
            state.resetBoardingDetectionCount();
            state.resetAlightingDetectionCount();

            log.info("승객 {} 자동 하차 처리 완료: 버스={}", state.getUserId(), bus.getBusNumber());
        } else {
            log.warn("승객 {} 자동 하차 처리 실패: 버스={}", state.getUserId(), bus.getBusNumber());
        }
    }

    /**
     * 버스와 거리 정보를 담는 내부 클래스
     */
    private static class BusDistance {
        final Bus bus;
        final double distance;

        BusDistance(Bus bus, double distance) {
            this.bus = bus;
            this.distance = distance;
        }
    }

    /**
     * 승객 상태 정보를 담는 내부 클래스
     */
    @Getter @Setter
    private static class PassengerState {
        private final String userId;
        private final String organizationId;
        private double latitude;
        private double longitude;
        private long timestamp;
        private boolean onBus;
        private String currentBusNumber;
        private int boardingDetectionCount;
        private String pendingBusNumber;
        private int alightingDetectionCount;

        PassengerState(String userId, String organizationId) {
            this.userId = userId;
            this.organizationId = organizationId;
            this.onBus = false;
            this.currentBusNumber = null;
            this.boardingDetectionCount = 0;
            this.pendingBusNumber = null;
            this.alightingDetectionCount = 0;
        }

        void updateLocation(double latitude, double longitude, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }

        void incrementBoardingDetectionCount(String busNumber) {
            if (pendingBusNumber == null || !pendingBusNumber.equals(busNumber)) {
                pendingBusNumber = busNumber;
                boardingDetectionCount = 1;
            } else {
                boardingDetectionCount++;
            }
        }

        void resetBoardingDetectionCount() {
            boardingDetectionCount = 0;
            pendingBusNumber = null;
        }

        void incrementAlightingDetectionCount() {
            alightingDetectionCount++;
        }

        void resetAlightingDetectionCount() {
            alightingDetectionCount = 0;
        }
    }
}