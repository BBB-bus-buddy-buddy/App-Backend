package capston2024.bustracker.service;

import capston2024.bustracker.config.dto.BusBoardingDTO;
import capston2024.bustracker.config.dto.BusRealTimeLocationDTO;
import capston2024.bustracker.config.dto.PassengerLocationDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.domain.PassengerTripEvent;
import capston2024.bustracker.repository.BusRepository;
import capston2024.bustracker.repository.PassengerTripEventRepository;
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
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PassengerLocationService {

    private final BusRepository busRepository;
    private final BusService busService;
    private final PassengerTripEventRepository passengerTripEventRepository;

    // 승객별 상태 관리 (userId -> 상태 맵)
    private final Map<String, PassengerState> passengerStates = new ConcurrentHashMap<>();

    // --- 추가: 탐지 결과를 명확히 하기 위한 Enum ---
    public enum DetectionResult {
        BOARDED,
        ALIGHTED,
        NO_CHANGE
    }

    // ================== 운행 중 탑승 감지를 위한 조정된 임계값 ==================
    private static final double AUTO_BOARDING_DISTANCE_THRESHOLD = 50.0;  // 50미터로 축소 (운행 중 감지)
    private static final double AUTO_ALIGHTING_DISTANCE_THRESHOLD = 100.0; // 100미터 유지
    private static final int CONSECUTIVE_DETECTION_THRESHOLD = 2;          // 2회로 증가 (정확도 향상)
    private static final long MIN_DWELL_TIME_SECONDS = 3;                  // 3초로 증가 (오탐지 방지)
    private static final long MIN_TRIP_TIME_SECONDS = 10;                  // 10초로 증가
    private static final long MIN_UPDATE_INTERVAL_MS = 1000;               // 1초로 증가
    private static final double GPS_JUMP_THRESHOLD = 500.0;                // 500m로 축소
    private static final double BUS_SPEED_THRESHOLD_KMH = 10.0;            // 시속 10km 이하일 때 탑승 가능
    // ===================================================================

    // --- 추가: 핸들러에서 승객 상태를 가져가기 위한 public 메서드 ---
    public PassengerState getPassengerState(String userId) {
        return passengerStates.get(userId);
    }

    /**
     * 승객 위치 정보 처리 - 배터리 최적화 및 정확도 개선
     * @param locationDTO 승객 위치 정보
     * @return 자동 탑승/하차 감지 여부
     */
    public DetectionResult processPassengerLocation(PassengerLocationDTO locationDTO) {
        String userId = locationDTO.getUserId();
        String organizationId = locationDTO.getOrganizationId();

        log.info("🎯 [위치처리] 승객 위치 처리 시작 - 사용자: {}, 조직: {}, 위치: ({}, {})",
                userId, organizationId, locationDTO.getLatitude(), locationDTO.getLongitude());

        log.debug("📊 [위치처리] 현재 설정값 - 탑승거리: {}m, 하차거리: {}m, 감지횟수: {}회, 대기시간: {}초",
                AUTO_BOARDING_DISTANCE_THRESHOLD, AUTO_ALIGHTING_DISTANCE_THRESHOLD,
                CONSECUTIVE_DETECTION_THRESHOLD, MIN_DWELL_TIME_SECONDS);

        PassengerState state = passengerStates.computeIfAbsent(userId,
                k -> {
                    log.info("👤 [위치처리] 새 승객 상태 생성 - 사용자: {}, 조직: {}", k, organizationId);
                    return new PassengerState(k, organizationId);
                });

        log.info("🚌 [위치처리] 승객 현재 상태 - 버스탑승: {}, 탑승버스: {}, 감지횟수(탑승/하차): {}/{}",
                state.isOnBus(), state.getCurrentBusNumber(),
                state.getBoardingDetectionCount(), state.getAlightingDetectionCount());

        if (!isValidLocationUpdate(state, locationDTO)) {
            log.warn("❌ [위치처리] 유효하지 않은 위치 업데이트 무시 - 사용자: {}", userId);
            return DetectionResult.NO_CHANGE;
        }

        log.info("✅ [위치처리] 위치 업데이트 유효성 검증 통과 - 사용자: {}", userId);

        state.updateLocation(locationDTO.getLatitude(), locationDTO.getLongitude(), locationDTO.getTimestamp());

        // 실시간 버스 위치 정보 조회
        log.info("🔍 [위치처리] 실시간 버스 위치 조회 시작 - 조직: {}", organizationId);
        Map<String, BusRealTimeLocationDTO> busLocations = busService.getCurrentBusLocations(organizationId);

        log.info("🚌 [위치처리] 실시간 버스 위치 조회 결과 - 총 {}대", busLocations.size());

        if (busLocations.isEmpty()) {
            log.warn("❌ [위치처리] 조직 {}에 활성화된 버스가 없음", organizationId);
            return DetectionResult.NO_CHANGE;
        }

        if (state.isOnBus()) {
            log.info("🚌 [위치처리] 승객이 버스에 탑승 중 - 하차 감지 처리 시작");
            return detectAlighting(state, busLocations);
        } else {
            log.info("🚶 [위치처리] 승객이 버스에 미탑승 - 탑승 감지 처리 시작");
            return detectBoarding(state, busLocations);
        }
    }

    /**
     * 위치 업데이트 유효성 검증 - 배터리 최적화
     */
    private boolean isValidLocationUpdate(PassengerState state, PassengerLocationDTO newLocation) {
        log.debug("🔍 [위치검증] 위치 업데이트 유효성 검사 시작 - 사용자: {}", state.getUserId());
        if (state.getLastUpdateTime() != 0) {
            long timeDiff = newLocation.getTimestamp() - state.getLastUpdateTime();
            log.debug("⏰ [위치검증] 시간 간격 체크 - 간격: {}ms, 최소요구: {}ms", timeDiff, MIN_UPDATE_INTERVAL_MS);

            if (timeDiff < MIN_UPDATE_INTERVAL_MS) {
                log.warn("❌ [위치검증] 업데이트 간격이 너무 짧음: {}ms < {}ms", timeDiff, MIN_UPDATE_INTERVAL_MS);
                return false;
            }
        }

        if (state.getLatitude() != 0 && state.getLongitude() != 0) {
            double distance = calculateDistance(
                    state.getLatitude(), state.getLongitude(),
                    newLocation.getLatitude(), newLocation.getLongitude()
            );

            log.debug("📏 [위치검증] 이동 거리 체크 - 거리: {}m", Math.round(distance));

            if (distance > GPS_JUMP_THRESHOLD && (newLocation.getTimestamp() - state.getLastUpdateTime()) < 60000) {
                log.warn("❌ [위치검증] GPS 점프 감지로 위치 업데이트 무시: 사용자={}, 거리={}m, 시간간격={}ms",
                        state.getUserId(), Math.round(distance),
                        (newLocation.getTimestamp() - state.getLastUpdateTime()));
                return false;
            }
        }

        log.info("✅ [위치검증] 위치 업데이트 유효성 검증 통과");
        return true;
    }

    /**
     * 탑승 감지 처리 - 운행 중 탑승 가능
     */
    private DetectionResult detectBoarding(PassengerState state, Map<String, BusRealTimeLocationDTO> busLocations) {
        log.info("🎫 [탑승감지] 탑승 감지 처리 시작 - 사용자: {}", state.getUserId());
        BusLocationDistance closestBus = findClosestBus(state, busLocations);

        if (closestBus != null) {
            log.info("🎯 [탑승감지] 가장 가까운 버스 발견 - 버스: {}, 거리: {}m, 속도: {}km/h",
                    closestBus.busNumber, Math.round(closestBus.distance), closestBus.estimatedSpeed);
        } else {
            log.debug("❌ [탑승감지] 가까운 버스 없음");
            return DetectionResult.NO_CHANGE;
        }

        if (closestBus != null && closestBus.distance <= AUTO_BOARDING_DISTANCE_THRESHOLD) {
            log.info("📍 [탑승감지] 탑승 거리 임계값 내 진입 - 버스: {}, 거리: {}m (임계값: {}m)",
                    closestBus.busNumber, Math.round(closestBus.distance), AUTO_BOARDING_DISTANCE_THRESHOLD);

            // 버스 속도 체크 (운행 중에도 느리게 움직이면 탑승 가능)
            if (closestBus.estimatedSpeed > BUS_SPEED_THRESHOLD_KMH) {
                log.debug("🚌 [탑승감지] 버스가 빠르게 이동 중 ({}km/h > {}km/h) - 대기",
                        closestBus.estimatedSpeed, BUS_SPEED_THRESHOLD_KMH);
                state.resetBoardingDetectionCount();
                return DetectionResult.NO_CHANGE;
            }

            if (!hasMinimumDwellTime(state, MIN_DWELL_TIME_SECONDS)) {
                log.debug("⏰ [탑승감지] 최소 대기 시간 미충족: {}초 필요", MIN_DWELL_TIME_SECONDS);
                return DetectionResult.NO_CHANGE;
            }

            log.info("✅ [탑승감지] 대기 시간 조건 충족 - 최소: {}초", MIN_DWELL_TIME_SECONDS);
            state.incrementBoardingDetectionCount(closestBus.busNumber);

            log.info("🔢 [탑승감지] 탑승 감지 카운트 증가 - 사용자: {}, 버스: {}, 거리: {}m, 감지횟수: {}/{}",
                    state.getUserId(), closestBus.busNumber,
                    Math.round(closestBus.distance), state.getBoardingDetectionCount(),
                    CONSECUTIVE_DETECTION_THRESHOLD);

            if (state.getBoardingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                log.info("🎉 [탑승감지] 승객 자동 탑승 감지 완료! - 사용자: {}, 버스: {}, 거리: {}m, 감지횟수: {}",
                        state.getUserId(), closestBus.busNumber,
                        Math.round(closestBus.distance), state.getBoardingDetectionCount());
                boolean success = processBoarding(state, closestBus.busNumber, closestBus.organizationId);
                return success ? DetectionResult.BOARDED : DetectionResult.NO_CHANGE;
            } else {
                log.info("⏳ [탑승감지] 감지 횟수 부족 - 계속 감지 중: {}/{}",
                        state.getBoardingDetectionCount(), CONSECUTIVE_DETECTION_THRESHOLD);
            }
        } else {
            if (state.getBoardingDetectionCount() > 0) {
                log.info("🔄 [탑승감지] 버스가 멀어져서 탑승 감지 카운트 리셋 - 사용자: {}", state.getUserId());
                state.resetBoardingDetectionCount();
            }
        }
        return DetectionResult.NO_CHANGE;
    }

    /**
     * 하차 감지 처리
     */
    private DetectionResult detectAlighting(PassengerState state, Map<String, BusRealTimeLocationDTO> busLocations) {
        log.info("🚪 [하차감지] 하차 감지 처리 시작 - 사용자: {}, 탑승버스: {}",
                state.getUserId(), state.getCurrentBusNumber());

        BusRealTimeLocationDTO onBusLocation = busLocations.get(state.getCurrentBusNumber());

        if (onBusLocation == null) {
            log.error("❌ [하차감지] 승객 {}가 탑승 중인 버스 {}의 위치를 찾을 수 없어 강제 하차 처리",
                    state.getUserId(), state.getCurrentBusNumber());
            processAlighting(state, state.getCurrentBusNumber(), state.getOrganizationId());
            return DetectionResult.ALIGHTED;
        }

        log.info("🚌 [하차감지] 탑승 중인 버스 확인됨 - 버스: {}, 위치: ({}, {})",
                onBusLocation.getBusNumber(),
                onBusLocation.getLatitude(),
                onBusLocation.getLongitude());

        double distance = calculateDistance(
                state.getLatitude(), state.getLongitude(),
                onBusLocation.getLatitude(), onBusLocation.getLongitude()
        );

        log.info("📏 [하차감지] 버스와의 거리 계산 - 사용자: {}, 버스: {}, 거리: {}m (임계값: {}m)",
                state.getUserId(), onBusLocation.getBusNumber(),
                Math.round(distance), AUTO_ALIGHTING_DISTANCE_THRESHOLD);

        if (!hasMinimumTripTime(state, MIN_TRIP_TIME_SECONDS)) {
            log.debug("⏰ [하차감지] 최소 여행 시간 미충족 - 필요: {}초", MIN_TRIP_TIME_SECONDS);
            return DetectionResult.NO_CHANGE;
        }

        log.info("✅ [하차감지] 최소 여행 시간 조건 충족 - 최소: {}초", MIN_TRIP_TIME_SECONDS);

        if (distance > AUTO_ALIGHTING_DISTANCE_THRESHOLD) {
            state.incrementAlightingDetectionCount();

            log.info("📍 [하차감지] 하차 거리 임계값 초과 - 사용자: {}, 버스: {}, 거리: {}m, 감지횟수: {}/{}",
                    state.getUserId(), onBusLocation.getBusNumber(),
                    Math.round(distance), state.getAlightingDetectionCount(),
                    CONSECUTIVE_DETECTION_THRESHOLD);

            if (state.getAlightingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                log.info("🎉 [하차감지] 승객 자동 하차 감지 완료! - 사용자: {}, 버스: {}, 거리: {}m, 감지횟수: {}",
                        state.getUserId(), onBusLocation.getBusNumber(),
                        Math.round(distance), state.getAlightingDetectionCount());
                boolean success = processAlighting(state, onBusLocation.getBusNumber(), onBusLocation.getOrganizationId());
                return success ? DetectionResult.ALIGHTED : DetectionResult.NO_CHANGE;
            } else {
                log.info("⏳ [하차감지] 감지 횟수 부족 - 계속 감지 중: {}/{}",
                        state.getAlightingDetectionCount(), CONSECUTIVE_DETECTION_THRESHOLD);
            }
        } else {
            if (state.getAlightingDetectionCount() > 0) {
                log.info("🔄 [하차감지] 버스와 가까워져서 하차 감지 카운트 리셋 - 사용자: {}", state.getUserId());
                state.resetAlightingDetectionCount();
            }
        }
        return DetectionResult.NO_CHANGE;
    }

    private boolean hasMinimumDwellTime(PassengerState state, long minSeconds) {
        if (state.getLocationSetTime() == 0) {
            log.debug("⏰ [체류시간] 위치 설정 시간이 없어서 대기 시간 확인 불가");
            return false;
        }
        long dwellTime = (System.currentTimeMillis() - state.getLocationSetTime()) / 1000;
        boolean result = dwellTime >= minSeconds;
        log.debug("⏰ [체류시간] 현재 위치 체류 시간 확인 - 사용자: {}, 체류시간: {}초, 최소요구: {}초, 결과: {}",
                state.getUserId(), dwellTime, minSeconds, result);
        if (!result) {
            log.debug("⏰ [체류시간] 대기 시간 부족: {}초 / {}초", dwellTime, minSeconds);
        }
        return result;
    }

    private boolean hasMinimumTripTime(PassengerState state, long minSeconds) {
        if (state.getBoardingTime() == null) {
            log.debug("⏰ [여행시간] 탑승 시간을 모르므로 여행 시간 확인 통과");
            return true;
        }
        long tripTime = (System.currentTimeMillis() - state.getBoardingTime()) / 1000;
        boolean result = tripTime >= minSeconds;
        log.debug("⏰ [여행시간] 버스 여행 시간 확인 - 사용자: {}, 여행시간: {}초, 최소요구: {}초, 결과: {}",
                state.getUserId(), tripTime, minSeconds, result);
        return result;
    }

    /**
     * 가장 가까운 버스 찾기 - 실시간 위치 기반
     */
    private BusLocationDistance findClosestBus(PassengerState state, Map<String, BusRealTimeLocationDTO> busLocations) {
        log.debug("🔍 [가까운버스] 가장 가까운 버스 찾기 시작 - 사용자: {}, 버스 수: {}",
                state.getUserId(), busLocations.size());

        BusLocationDistance closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Map.Entry<String, BusRealTimeLocationDTO> entry : busLocations.entrySet()) {
            BusRealTimeLocationDTO busLocation = entry.getValue();

            double distance = calculateDistance(
                    state.getLatitude(), state.getLongitude(),
                    busLocation.getLatitude(), busLocation.getLongitude()
            );

            // 버스 속도 추정 (이전 위치 정보가 있다면)
            double estimatedSpeed = estimateBusSpeed(busLocation);

            log.debug("📏 [가까운버스] 버스 {} 거리: {}m, 추정속도: {}km/h",
                    busLocation.getBusNumber(), Math.round(distance), estimatedSpeed);

            if (distance < minDistance) {
                minDistance = distance;
                closest = new BusLocationDistance(
                        busLocation.getBusNumber(),
                        busLocation.getOrganizationId(),
                        distance,
                        estimatedSpeed
                );
                log.debug("🎯 [가까운버스] 새로운 최단거리 버스 발견 - 버스: {}, 거리: {}m",
                        busLocation.getBusNumber(), Math.round(distance));
            }
        }

        if (closest != null) {
            log.info("✅ [가까운버스] 가장 가까운 버스 확정 - 버스: {}, 거리: {}m, 속도: {}km/h",
                    closest.busNumber, Math.round(closest.distance), closest.estimatedSpeed);
        } else {
            log.warn("❌ [가까운버스] 위치 정보가 있는 버스가 없음");
        }

        return closest;
    }

    /**
     * 버스 속도 추정 - 간단한 구현
     */
    private double estimateBusSpeed(BusRealTimeLocationDTO busLocation) {
        // TODO: 이전 위치 정보를 저장하여 실제 속도 계산
        // 현재는 모든 버스가 정차 중이라고 가정
        return 0.0;
    }
          
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == lat2 && lon1 == lon2) return 0;
        final double R = 6371000;
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
     * 탑승 처리 - 버스 번호와 조직 ID 사용
     */
    private boolean processBoarding(PassengerState state, String busNumber, String organizationId) {
        log.info("🎫 [탑승처리] 자동 탑승 처리 시작 - 사용자: {}, 버스: {}", state.getUserId(), busNumber);
        try {
            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber(busNumber);
            boardingDTO.setOrganizationId(organizationId);
            boardingDTO.setUserId(state.getUserId());
            boardingDTO.setAction(BusBoardingDTO.BoardingAction.BOARD);
            boardingDTO.setTimestamp(System.currentTimeMillis());

            log.info("📋 [탑승처리] 탑승 DTO 생성 완료 - {}", boardingDTO);
            log.info("🚀 [탑승처리] BusService.processBusBoarding 호출 시작");

            boolean success = busService.processBusBoarding(boardingDTO);

            log.info("🎯 [탑승처리] BusService.processBusBoarding 호출 결과: {}", success);

            if (success) {
                state.setOnBus(true);
                state.setCurrentBusNumber(busNumber);
                state.setBoardingTime(System.currentTimeMillis());
                state.resetBoardingDetectionCount();
                state.resetAlightingDetectionCount();
                recordTripEvent(state, busNumber, PassengerTripEvent.EventType.BOARD, null, null);
                log.info("🎉 [탑승처리] 승객 자동 탑승 처리 완료! - 사용자: {}, 버스: {}",
                        state.getUserId(), busNumber);
                return true;
            } else {
                log.warn("❌ [탑승처리] 승객 자동 탑승 처리 실패 - 사용자: {}, 버스: {} (좌석 부족 또는 운행 중지)",
                        state.getUserId(), busNumber);
                state.resetBoardingDetectionCount();
                return false;
            }
        } catch (Exception e) {
            log.error("❌ [탑승처리] 자동 탑승 처리 중 오류 발생 - 사용자: {}, 버스: {}, 오류: {}",
                    state.getUserId(), busNumber, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 하차 처리 - 버스 번호와 조직 ID 사용
     */
    private boolean processAlighting(PassengerState state, String busNumber, String organizationId) {
        log.info("🚪 [하차처리] 자동 하차 처리 시작 - 사용자: {}, 버스: {}", state.getUserId(), busNumber);
        try {
            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber(busNumber);
            boardingDTO.setOrganizationId(organizationId);
            boardingDTO.setUserId(state.getUserId());
            boardingDTO.setAction(BusBoardingDTO.BoardingAction.ALIGHT);
            boardingDTO.setTimestamp(System.currentTimeMillis());

            log.info("📋 [하차처리] 하차 DTO 생성 완료 - {}", boardingDTO);
            log.info("🚀 [하차처리] BusService.processBusBoarding 호출 시작");

            boolean success = busService.processBusBoarding(boardingDTO);

            log.info("🎯 [하차처리] BusService.processBusBoarding 호출 결과: {}", success);
          
            if (success) {
                state.setOnBus(false);
                state.setCurrentBusNumber(null);
                state.setBoardingTime(null);
                state.resetBoardingDetectionCount();
                state.resetAlightingDetectionCount();
                recordTripEvent(state, busNumber, PassengerTripEvent.EventType.ALIGHT, null, null);
                log.info("🎉 [하차처리] 승객 자동 하차 처리 완료! - 사용자: {}, 버스: {}",
                        state.getUserId(), busNumber);
                return true;
            } else {
                log.warn("❌ [하차처리] 승객 자동 하차 처리 실패 - 사용자: {}, 버스: {}",
                        state.getUserId(), busNumber);
                state.resetAlightingDetectionCount();
                return false;
            }
        } catch (Exception e) {
            log.error("❌ [하차처리] 자동 하차 처리 중 오류 발생 - 사용자: {}, 버스: {}, 오류: {}",
                    state.getUserId(), busNumber, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 버스 위치 정보와 거리를 담는 내부 클래스
     */
    private static class BusLocationDistance {
        final String busNumber;
        final String organizationId;
        final double distance;
        final double estimatedSpeed;

        BusLocationDistance(String busNumber, String organizationId, double distance, double estimatedSpeed) {
            this.busNumber = busNumber;
            this.organizationId = organizationId;
            this.distance = distance;
            this.estimatedSpeed = estimatedSpeed;
        }
    }

    @Getter @Setter
    public static class PassengerState {
        private final String userId;
        private final String organizationId;
        private double latitude;
        private double longitude;
        private long lastUpdateTime;
        private long locationSetTime;
        private boolean onBus;
        private String currentBusNumber;
        private Long boardingTime;
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
            log.info("👤 [승객상태] 새 승객 상태 객체 생성 - 사용자: {}, 조직: {}", userId, organizationId);
        }

        void updateLocation(double latitude, double longitude, long timestamp) {
            log.debug("📍 [승객상태] 위치 업데이트 - 사용자: {}, 위치: ({}, {})",
                    userId, latitude, longitude);
            if (this.latitude == 0 && this.longitude == 0) {
                this.locationSetTime = timestamp;
                log.info("📍 [승객상태] 첫 번째 위치 설정 - 사용자: {}, 시간: {}",
                        userId, new java.util.Date(timestamp));
            } else {
                double distance = calculateDistanceSimple(this.latitude, this.longitude, latitude, longitude);
                log.debug("📏 [승객상태] 이전 위치와의 거리: {}m", Math.round(distance));
                if (distance > 20) {
                    this.locationSetTime = timestamp;
                    log.info("📍 [승객상태] 위치 변경 감지 ({}m 이동) - 위치 설정 시간 갱신", Math.round(distance));
                }
            }
            this.latitude = latitude;
            this.longitude = longitude;
            this.lastUpdateTime = timestamp;
            log.debug("✅ [승객상태] 위치 업데이트 완료 - 사용자: {}", userId);
        }

        private double calculateDistanceSimple(double lat1, double lon1, double lat2, double lon2) {
            double deltaLat = lat1 - lat2;
            double deltaLon = lon1 - lon2;
            return Math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon) * 111000;
        }

        void incrementBoardingDetectionCount(String busNumber) {
            if (pendingBusNumber == null || !pendingBusNumber.equals(busNumber)) {
                log.info("🔢 [승객상태] 새 버스로 탑승 감지 시작 - 사용자: {}, 버스: {} (이전: {})",
                        userId, busNumber, pendingBusNumber);
                pendingBusNumber = busNumber;
                boardingDetectionCount = 1;
            } else {
                boardingDetectionCount++;
                log.info("🔢 [승객상태] 탑승 감지 카운트 증가 - 사용자: {}, 버스: {}, 횟수: {}",
                        userId, busNumber, boardingDetectionCount);
            }
        }

        void resetBoardingDetectionCount() {
            if (boardingDetectionCount > 0) {
                log.info("🔄 [승객상태] 탑승 감지 카운트 리셋 - 사용자: {}, 이전 카운트: {}",
                        userId, boardingDetectionCount);
            }
            boardingDetectionCount = 0;
            pendingBusNumber = null;
        }

        void incrementAlightingDetectionCount() {
            alightingDetectionCount++;
            log.info("🔢 [승객상태] 하차 감지 카운트 증가 - 사용자: {}, 횟수: {}",
                    userId, alightingDetectionCount);
        }

        void resetAlightingDetectionCount() {
            if (alightingDetectionCount > 0) {
                log.info("🔄 [승객상태] 하차 감지 카운트 리셋 - 사용자: {}, 이전 카운트: {}",
                        userId, alightingDetectionCount);
            }
            alightingDetectionCount = 0;
        }
    }

    private void recordTripEvent(PassengerState state,
                                 String busNumber,
                                 PassengerTripEvent.EventType eventType,
                                 Double distanceToBus,
                                 Double estimatedSpeed) {
        if (state == null) {
            return;
        }
        try {
            String stationId = resolveRecentStationId(busNumber, state.getOrganizationId());
            PassengerTripEvent event = PassengerTripEvent.builder()
                    .userId(state.getUserId())
                    .organizationId(state.getOrganizationId())
                    .busNumber(busNumber)
                    .stationId(stationId)
                    .eventType(eventType)
                    .latitude(state.getLatitude())
                    .longitude(state.getLongitude())
                    .distanceToBus(distanceToBus)
                    .estimatedBusSpeed(estimatedSpeed)
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of(
                            "source", "AUTO_DETECTION"
                    ))
                    .build();
            passengerTripEventRepository.save(event);
            log.debug("📝 [이벤트기록] 승객 이벤트 저장 완료 - userId={}, type={}, station={}",
                    state.getUserId(), eventType, stationId);
        } catch (Exception e) {
            log.error("❌ [이벤트기록] 승객 이벤트 저장 실패 - 사용자: {}, 이벤트: {}, 오류: {}",
                    state.getUserId(), eventType, e.getMessage(), e);
        }
    }

    private String resolveRecentStationId(String busNumber, String organizationId) {
        if (busNumber == null || organizationId == null) {
            return null;
        }
        try {
            Bus bus = busService.getBusByNumberAndOrganization(busNumber, organizationId);
            return bus.getPrevStationId();
        } catch (Exception e) {
            log.debug("⚠️ [이벤트기록] 최근 정류장 조회 실패 - busNumber={}, organizationId={}, 오류={}",
                    busNumber, organizationId, e.getMessage());
            return null;
        }
    }
}
