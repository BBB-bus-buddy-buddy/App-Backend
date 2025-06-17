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

import java.time.Instant;
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

    // **더 현실적으로 조정된 임계값들**
    private static final double AUTO_BOARDING_DISTANCE_THRESHOLD = 30.0; // 30미터로 감소 (더 민감하게)
    private static final double AUTO_ALIGHTING_DISTANCE_THRESHOLD = 80.0; // 80미터로 감소
    private static final int CONSECUTIVE_DETECTION_THRESHOLD = 3; // 3회로 감소 (더 빠른 감지)
    private static final long MIN_DWELL_TIME_SECONDS = 15; // 15초로 대폭 감소 (정류장 대기 시간 고려)
    private static final long MIN_TRIP_TIME_SECONDS = 60; // 최소 1분 이동 시간으로 감소
    private static final long MIN_UPDATE_INTERVAL_MS = 3000; // 3초로 감소 (10초는 너무 김)
    private static final double GPS_JUMP_THRESHOLD = 200.0; // 200m로 증가 (GPS 오차 허용)

    /**
     * 승객 위치 정보 처리 - 배터리 최적화 및 정확도 개선
     * @param locationDTO 승객 위치 정보
     * @return 자동 탑승/하차 감지 여부
     */
    public boolean processPassengerLocation(PassengerLocationDTO locationDTO) {
        String userId = locationDTO.getUserId();
        String organizationId = locationDTO.getOrganizationId();

        log.debug("승객 위치 처리 시작: 사용자={}, 위치=({}, {})",
                userId, locationDTO.getLatitude(), locationDTO.getLongitude());

        // 승객 상태 조회 또는 생성
        PassengerState state = passengerStates.computeIfAbsent(userId,
                k -> new PassengerState(userId, organizationId));

        // 위치 업데이트 및 유효성 검증
        if (!isValidLocationUpdate(state, locationDTO)) {
            log.debug("유효하지 않은 위치 업데이트 무시: 사용자 = {}", userId);
            return false;
        }

        state.updateLocation(locationDTO.getLatitude(), locationDTO.getLongitude(), locationDTO.getTimestamp());

        // 조직의 운행 중인 버스만 조회 (성능 최적화)
        List<Bus> operatingBuses = busRepository.findByOrganizationIdAndIsOperateTrue(organizationId);

        if (operatingBuses.isEmpty()) {
            log.debug("조직 {}에 운행 중인 버스가 없습니다", organizationId);
            return false;
        }

        log.debug("운행 중인 버스 수: {}", operatingBuses.size());

        // 승객 상태에 따른 처리
        if (state.isOnBus()) {
            // 이미 버스에 탑승 중인 경우 - 하차 감지 처리
            return detectAlighting(state, operatingBuses);
        } else {
            // 버스에 탑승하지 않은 경우 - 탑승 감지 처리
            return detectBoarding(state, operatingBuses);
        }
    }

    /**
     * 위치 업데이트 유효성 검증 - 배터리 최적화
     */
    private boolean isValidLocationUpdate(PassengerState state, PassengerLocationDTO newLocation) {
        // 너무 자주 업데이트하는 것을 방지 (최소 3초 간격으로 완화)
        if (state.getLastUpdateTime() != 0) {
            long timeDiff = newLocation.getTimestamp() - state.getLastUpdateTime();
            if (timeDiff < MIN_UPDATE_INTERVAL_MS) {
                log.trace("업데이트 간격이 너무 짧음: {}ms < {}ms", timeDiff, MIN_UPDATE_INTERVAL_MS);
                return false;
            }
        }

        // GPS 정확도가 너무 낮은 경우 무시 (200m 이상 점프)
        if (state.getLatitude() != 0 && state.getLongitude() != 0) {
            double distance = calculateDistance(
                    state.getLatitude(), state.getLongitude(),
                    newLocation.getLatitude(), newLocation.getLongitude()
            );

            // 200m 이상 점프는 GPS 오류로 간주 (단, 장시간 후 업데이트는 허용)
            if (distance > GPS_JUMP_THRESHOLD && (newLocation.getTimestamp() - state.getLastUpdateTime()) < 60000) {
                log.warn("GPS 점프 감지로 위치 업데이트 무시: 사용자 = {}, 거리 = {}m",
                        state.getUserId(), distance);
                return false;
            }
        }

        return true;
    }

    /**
     * 탑승 감지 처리 - 정확도 개선
     */
    private boolean detectBoarding(PassengerState state, List<Bus> buses) {
        // 가장 가까운 버스와 거리 찾기
        BusDistance closestBus = findClosestBus(state, buses);

        if (closestBus != null) {
            log.debug("가장 가까운 버스: {}, 거리: {}m",
                    closestBus.bus.getBusNumber(), Math.round(closestBus.distance));
        }

        if (closestBus != null && closestBus.distance <= AUTO_BOARDING_DISTANCE_THRESHOLD) {
            // **디버깅 로그 추가**
            log.info("탑승 감지 진행 중: 사용자={}, 버스={}, 거리={}m, 감지횟수={}/{}",
                    state.getUserId(), closestBus.bus.getBusNumber(),
                    Math.round(closestBus.distance), state.getBoardingDetectionCount() + 1,
                    CONSECUTIVE_DETECTION_THRESHOLD);

            // 1. 버스가 실제로 정차 중인지 확인 (현재는 항상 true)
            if (!isBusStationary(closestBus.bus)) {
                log.debug("버스가 이동 중이므로 탑승 감지 건너뜀: 버스 = {}", closestBus.bus.getBusNumber());
                state.resetBoardingDetectionCount();
                return false;
            }

            // 2. 승객이 일정 시간 이상 머물렀는지 확인 (15초로 감소)
            if (!hasMinimumDwellTime(state, MIN_DWELL_TIME_SECONDS)) {
                log.debug("최소 대기 시간 미충족: {}초 필요", MIN_DWELL_TIME_SECONDS);
                return false;
            }

            // 3. 연속 감지 카운트 증가
            state.incrementBoardingDetectionCount(closestBus.bus.getBusNumber());

            // 4. 임계값 초과 시 탑승 처리
            if (state.getBoardingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                log.info("승객 {} 자동 탑승 감지 완료: 버스={}, 거리={}m, 감지횟수={}",
                        state.getUserId(), closestBus.bus.getBusNumber(),
                        Math.round(closestBus.distance), state.getBoardingDetectionCount());

                // 탑승 처리
                return processBoarding(state, closestBus.bus);
            }
        } else {
            // 가까운 버스가 없으면 카운트 리셋
            if (state.getBoardingDetectionCount() > 0) {
                log.debug("버스가 멀어져서 탑승 감지 카운트 리셋");
                state.resetBoardingDetectionCount();
            }
        }

        return false;
    }

    /**
     * 하차 감지 처리 - 정확도 개선
     */
    private boolean detectAlighting(PassengerState state, List<Bus> buses) {
        // 현재 탑승 중인 버스 찾기
        Bus onBus = buses.stream()
                .filter(bus -> bus.getBusNumber().equals(state.getCurrentBusNumber()))
                .findFirst()
                .orElse(null);

        if (onBus == null) {
            log.warn("승객 {}가 탑승 중인 버스 {}를 찾을 수 없어 강제 하차 처리",
                    state.getUserId(), state.getCurrentBusNumber());
            state.setOnBus(false);
            state.setCurrentBusNumber(null);
            return false;
        }

        // 버스와의 거리 계산
        double distance = calculateDistance(
                state.getLatitude(), state.getLongitude(),
                onBus.getLocation().getY(), onBus.getLocation().getX()
        );

        log.debug("하차 감지 중: 사용자={}, 버스={}, 거리={}m",
                state.getUserId(), onBus.getBusNumber(), Math.round(distance));

        // 1. 최소 여행 시간 확인 (너무 빨리 하차하는 것 방지)
        if (!hasMinimumTripTime(state, MIN_TRIP_TIME_SECONDS)) {
            return false;
        }

        // 2. 거리가 임계값을 초과하면 하차 감지 카운트 증가
        if (distance > AUTO_ALIGHTING_DISTANCE_THRESHOLD) {
            state.incrementAlightingDetectionCount();

            log.info("하차 감지 진행 중: 사용자={}, 버스={}, 거리={}m, 감지횟수={}/{}",
                    state.getUserId(), onBus.getBusNumber(),
                    Math.round(distance), state.getAlightingDetectionCount(),
                    CONSECUTIVE_DETECTION_THRESHOLD);

            // 3. 연속 감지 횟수가 임계값을 초과하면 하차 처리
            if (state.getAlightingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                log.info("승객 {} 자동 하차 감지 완료: 버스={}, 거리={}m, 감지횟수={}",
                        state.getUserId(), onBus.getBusNumber(),
                        Math.round(distance), state.getAlightingDetectionCount());

                // 하차 처리
                return processAlighting(state, onBus);
            }
        } else {
            // 버스와 거리가 가까우면 카운트 리셋
            if (state.getAlightingDetectionCount() > 0) {
                log.debug("버스와 가까워져서 하차 감지 카운트 리셋");
                state.resetAlightingDetectionCount();
            }
        }

        return false;
    }

    /**
     * 버스가 정차 중인지 확인 (속도 기반)
     */
    private boolean isBusStationary(Bus bus) {
        // TODO: 향후 버스의 속도 정보를 활용하여 정차 여부 판단
        // 현재는 항상 true 반환하여 탑승 감지를 허용
        return true;
    }

    /**
     * 최소 체류 시간 확인
     */
    private boolean hasMinimumDwellTime(PassengerState state, long minSeconds) {
        if (state.getLocationSetTime() == 0) {
            return false;
        }

        long dwellTime = (System.currentTimeMillis() - state.getLocationSetTime()) / 1000;
        boolean result = dwellTime >= minSeconds;

        if (!result) {
            log.trace("대기 시간 부족: {}초 / {}초", dwellTime, minSeconds);
        }

        return result;
    }

    /**
     * 최소 여행 시간 확인 (너무 빨리 하차하는 것 방지)
     */
    private boolean hasMinimumTripTime(PassengerState state, long minSeconds) {
        if (state.getBoardingTime() == null) {
            return true; // 탑승 시간을 모르면 통과
        }

        long tripTime = (System.currentTimeMillis() - state.getBoardingTime()) / 1000;
        return tripTime >= minSeconds;
    }

    /**
     * 가장 가까운 버스와 거리 찾기 - 성능 최적화
     */
    private BusDistance findClosestBus(PassengerState state, List<Bus> buses) {
        BusDistance closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Bus bus : buses) {
            if (bus.getLocation() == null) {
                log.debug("버스 {}의 위치 정보가 없음", bus.getBusNumber());
                continue;
            }

            double distance = calculateDistance(
                    state.getLatitude(), state.getLongitude(),
                    bus.getLocation().getY(), bus.getLocation().getX()
            );

            log.trace("버스 {} 거리: {}m", bus.getBusNumber(), Math.round(distance));

            if (distance < minDistance) {
                minDistance = distance;
                closest = new BusDistance(bus, distance);
            }
        }

        return closest;
    }

    /**
     * 두 위치 사이의 거리 계산 (Haversine 공식) - 최적화된 버전
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == lat2 && lon1 == lon2) {
            return 0;
        }

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
    private boolean processBoarding(PassengerState state, Bus bus) {
        try {
            log.info("자동 탑승 처리 시작: 사용자={}, 버스={}", state.getUserId(), bus.getBusNumber());

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
                state.setBoardingTime(System.currentTimeMillis());
                state.resetBoardingDetectionCount();
                state.resetAlightingDetectionCount();

                log.info("승객 {} 자동 탑승 처리 완료: 버스={}, 현재 승객수={}",
                        state.getUserId(), bus.getBusNumber(), bus.getOccupiedSeats() + 1);
                return true;
            } else {
                log.warn("승객 {} 자동 탑승 처리 실패: 버스={} (좌석 부족 또는 운행 중지)",
                        state.getUserId(), bus.getBusNumber());
                state.resetBoardingDetectionCount(); // 실패 시 카운트 리셋
                return false;
            }
        } catch (Exception e) {
            log.error("자동 탑승 처리 중 오류 발생: 사용자={}, 버스={}, 오류={}",
                    state.getUserId(), bus.getBusNumber(), e.getMessage());
            return false;
        }
    }

    /**
     * 자동 하차 처리
     */
    private boolean processAlighting(PassengerState state, Bus bus) {
        try {
            log.info("자동 하차 처리 시작: 사용자={}, 버스={}", state.getUserId(), bus.getBusNumber());

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
                state.setBoardingTime(null);
                state.resetBoardingDetectionCount();
                state.resetAlightingDetectionCount();

                log.info("승객 {} 자동 하차 처리 완료: 버스={}", state.getUserId(), bus.getBusNumber());
                return true;
            } else {
                log.warn("승객 {} 자동 하차 처리 실패: 버스={}", state.getUserId(), bus.getBusNumber());
                state.resetAlightingDetectionCount(); // 실패 시 카운트 리셋
                return false;
            }
        } catch (Exception e) {
            log.error("자동 하차 처리 중 오류 발생: 사용자={}, 버스={}, 오류={}",
                    state.getUserId(), bus.getBusNumber(), e.getMessage());
            return false;
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
     * 승객 상태 정보를 담는 내부 클래스 - 개선된 버전
     */
    @Getter @Setter
    private static class PassengerState {
        private final String userId;
        private final String organizationId;
        private double latitude;
        private double longitude;
        private long lastUpdateTime;
        private long locationSetTime; // 현재 위치에 처음 도달한 시간
        private boolean onBus;
        private String currentBusNumber;
        private Long boardingTime; // 탑승 시간
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
            this.boardingTime = null;
        }

        void updateLocation(double latitude, double longitude, long timestamp) {
            // 위치가 크게 변경되었으면 locationSetTime 갱신
            if (this.latitude == 0 && this.longitude == 0) {
                // 첫 번째 위치 설정
                this.locationSetTime = timestamp;
            } else {
                double distance = calculateDistanceSimple(this.latitude, this.longitude, latitude, longitude);
                if (distance > 20) { // 20m 이상 이동시 위치 변경으로 간주
                    this.locationSetTime = timestamp;
                }
            }

            this.latitude = latitude;
            this.longitude = longitude;
            this.lastUpdateTime = timestamp;
        }

        private double calculateDistanceSimple(double lat1, double lon1, double lat2, double lon2) {
            // 간단한 유클리드 거리 계산 (성능 최적화)
            double deltaLat = lat1 - lat2;
            double deltaLon = lon1 - lon2;
            return Math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon) * 111000; // 대략적인 미터 변환
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