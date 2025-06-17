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

        log.info("🎯 [위치처리] 승객 위치 처리 시작 - 사용자: {}, 조직: {}, 위치: ({}, {})",
                userId, organizationId, locationDTO.getLatitude(), locationDTO.getLongitude());

        log.debug("📊 [위치처리] 현재 설정값 - 탑승거리: {}m, 하차거리: {}m, 감지횟수: {}회, 대기시간: {}초",
                AUTO_BOARDING_DISTANCE_THRESHOLD, AUTO_ALIGHTING_DISTANCE_THRESHOLD,
                CONSECUTIVE_DETECTION_THRESHOLD, MIN_DWELL_TIME_SECONDS);

        // 승객 상태 조회 또는 생성
        PassengerState state = passengerStates.computeIfAbsent(userId,
                k -> {
                    log.info("👤 [위치처리] 새 승객 상태 생성 - 사용자: {}, 조직: {}", k, organizationId);
                    return new PassengerState(k, organizationId);
                });

        log.info("🚌 [위치처리] 승객 현재 상태 - 버스탑승: {}, 탑승버스: {}, 감지횟수(탑승/하차): {}/{}",
                state.isOnBus(), state.getCurrentBusNumber(),
                state.getBoardingDetectionCount(), state.getAlightingDetectionCount());

        // 위치 업데이트 및 유효성 검증
        if (!isValidLocationUpdate(state, locationDTO)) {
            log.warn("❌ [위치처리] 유효하지 않은 위치 업데이트 무시 - 사용자: {}", userId);
            return false;
        }

        log.info("✅ [위치처리] 위치 업데이트 유효성 검증 통과 - 사용자: {}", userId);

        state.updateLocation(locationDTO.getLatitude(), locationDTO.getLongitude(), locationDTO.getTimestamp());

        // 조직의 운행 중인 버스만 조회 (성능 최적화)
        log.info("🔍 [위치처리] 운행 중인 버스 조회 시작 - 조직: {}", organizationId);
        List<Bus> operatingBuses = busRepository.findByOrganizationIdAndIsOperateTrue(organizationId);

        log.info("🚌 [위치처리] 운행 중인 버스 조회 결과 - 총 {}대", operatingBuses.size());

        if (operatingBuses.isEmpty()) {
            log.warn("❌ [위치처리] 조직 {}에 운행 중인 버스가 없음", organizationId);
            return false;
        }

        // 운행 중인 버스 상세 정보 로깅
        for (Bus bus : operatingBuses) {
            log.debug("🚌 [위치처리] 운행 버스 정보 - 번호: {}, 위치: ({}, {}), 좌석: {}/{}",
                    bus.getBusNumber(),
                    bus.getLocation() != null ? bus.getLocation().getY() : "null",
                    bus.getLocation() != null ? bus.getLocation().getX() : "null",
                    bus.getOccupiedSeats(), bus.getTotalSeats());
        }

        // 승객 상태에 따른 처리
        if (state.isOnBus()) {
            log.info("🚌 [위치처리] 승객이 버스에 탑승 중 - 하차 감지 처리 시작");
            // 이미 버스에 탑승 중인 경우 - 하차 감지 처리
            return detectAlighting(state, operatingBuses);
        } else {
            log.info("🚶 [위치처리] 승객이 버스에 미탑승 - 탑승 감지 처리 시작");
            // 버스에 탑승하지 않은 경우 - 탑승 감지 처리
            return detectBoarding(state, operatingBuses);
        }
    }

    /**
     * 위치 업데이트 유효성 검증 - 배터리 최적화
     */
    private boolean isValidLocationUpdate(PassengerState state, PassengerLocationDTO newLocation) {
        log.debug("🔍 [위치검증] 위치 업데이트 유효성 검사 시작 - 사용자: {}", state.getUserId());

        // 너무 자주 업데이트하는 것을 방지 (최소 3초 간격으로 완화)
        if (state.getLastUpdateTime() != 0) {
            long timeDiff = newLocation.getTimestamp() - state.getLastUpdateTime();
            log.debug("⏰ [위치검증] 시간 간격 체크 - 간격: {}ms, 최소요구: {}ms", timeDiff, MIN_UPDATE_INTERVAL_MS);

            if (timeDiff < MIN_UPDATE_INTERVAL_MS) {
                log.warn("❌ [위치검증] 업데이트 간격이 너무 짧음: {}ms < {}ms", timeDiff, MIN_UPDATE_INTERVAL_MS);
                return false;
            }
        }

        // GPS 정확도가 너무 낮은 경우 무시 (200m 이상 점프)
        if (state.getLatitude() != 0 && state.getLongitude() != 0) {
            double distance = calculateDistance(
                    state.getLatitude(), state.getLongitude(),
                    newLocation.getLatitude(), newLocation.getLongitude()
            );

            log.debug("📏 [위치검증] 이동 거리 체크 - 거리: {}m", Math.round(distance));

            // 200m 이상 점프는 GPS 오류로 간주 (단, 장시간 후 업데이트는 허용)
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
     * 탑승 감지 처리 - 정확도 개선
     */
    private boolean detectBoarding(PassengerState state, List<Bus> buses) {
        log.info("🎫 [탑승감지] 탑승 감지 처리 시작 - 사용자: {}", state.getUserId());

        // 가장 가까운 버스와 거리 찾기
        BusDistance closestBus = findClosestBus(state, buses);

        if (closestBus != null) {
            log.info("🎯 [탑승감지] 가장 가까운 버스 발견 - 버스: {}, 거리: {}m",
                    closestBus.bus.getBusNumber(), Math.round(closestBus.distance));
        } else {
            log.debug("❌ [탑승감지] 가까운 버스 없음");
            return false;
        }

        if (closestBus != null && closestBus.distance <= AUTO_BOARDING_DISTANCE_THRESHOLD) {
            log.info("📍 [탑승감지] 탑승 거리 임계값 내 진입 - 버스: {}, 거리: {}m (임계값: {}m)",
                    closestBus.bus.getBusNumber(), Math.round(closestBus.distance), AUTO_BOARDING_DISTANCE_THRESHOLD);

            // 1. 버스가 실제로 정차 중인지 확인 (현재는 항상 true)
            if (!isBusStationary(closestBus.bus)) {
                log.debug("🚌 [탑승감지] 버스가 이동 중이므로 탑승 감지 건너뜀 - 버스: {}", closestBus.bus.getBusNumber());
                state.resetBoardingDetectionCount();
                return false;
            }

            // 2. 승객이 일정 시간 이상 머물렀는지 확인 (15초로 감소)
            if (!hasMinimumDwellTime(state, MIN_DWELL_TIME_SECONDS)) {
                log.debug("⏰ [탑승감지] 최소 대기 시간 미충족: {}초 필요", MIN_DWELL_TIME_SECONDS);
                return false;
            }

            log.info("✅ [탑승감지] 대기 시간 조건 충족 - 최소: {}초", MIN_DWELL_TIME_SECONDS);

            // 3. 연속 감지 카운트 증가
            state.incrementBoardingDetectionCount(closestBus.bus.getBusNumber());

            log.info("🔢 [탑승감지] 탑승 감지 카운트 증가 - 사용자: {}, 버스: {}, 거리: {}m, 감지횟수: {}/{}",
                    state.getUserId(), closestBus.bus.getBusNumber(),
                    Math.round(closestBus.distance), state.getBoardingDetectionCount(),
                    CONSECUTIVE_DETECTION_THRESHOLD);

            // 4. 임계값 초과 시 탑승 처리
            if (state.getBoardingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                log.info("🎉 [탑승감지] 승객 자동 탑승 감지 완료! - 사용자: {}, 버스: {}, 거리: {}m, 감지횟수: {}",
                        state.getUserId(), closestBus.bus.getBusNumber(),
                        Math.round(closestBus.distance), state.getBoardingDetectionCount());

                // 탑승 처리
                return processBoarding(state, closestBus.bus);
            } else {
                log.info("⏳ [탑승감지] 감지 횟수 부족 - 계속 감지 중: {}/{}",
                        state.getBoardingDetectionCount(), CONSECUTIVE_DETECTION_THRESHOLD);
            }
        } else {
            // 가까운 버스가 없으면 카운트 리셋
            if (state.getBoardingDetectionCount() > 0) {
                log.info("🔄 [탑승감지] 버스가 멀어져서 탑승 감지 카운트 리셋 - 사용자: {}", state.getUserId());
                state.resetBoardingDetectionCount();
            }
        }

        return false;
    }

    /**
     * 하차 감지 처리 - 정확도 개선
     */
    private boolean detectAlighting(PassengerState state, List<Bus> buses) {
        log.info("🚪 [하차감지] 하차 감지 처리 시작 - 사용자: {}, 탑승버스: {}",
                state.getUserId(), state.getCurrentBusNumber());

        // 현재 탑승 중인 버스 찾기
        Bus onBus = buses.stream()
                .filter(bus -> bus.getBusNumber().equals(state.getCurrentBusNumber()))
                .findFirst()
                .orElse(null);

        if (onBus == null) {
            log.error("❌ [하차감지] 승객 {}가 탑승 중인 버스 {}를 찾을 수 없어 강제 하차 처리",
                    state.getUserId(), state.getCurrentBusNumber());
            state.setOnBus(false);
            state.setCurrentBusNumber(null);
            return false;
        }

        log.info("🚌 [하차감지] 탑승 중인 버스 확인됨 - 버스: {}, 위치: ({}, {})",
                onBus.getBusNumber(),
                onBus.getLocation() != null ? onBus.getLocation().getY() : "null",
                onBus.getLocation() != null ? onBus.getLocation().getX() : "null");

        // 버스와의 거리 계산
        double distance = calculateDistance(
                state.getLatitude(), state.getLongitude(),
                onBus.getLocation().getY(), onBus.getLocation().getX()
        );

        log.info("📏 [하차감지] 버스와의 거리 계산 - 사용자: {}, 버스: {}, 거리: {}m (임계값: {}m)",
                state.getUserId(), onBus.getBusNumber(), Math.round(distance), AUTO_ALIGHTING_DISTANCE_THRESHOLD);

        // 1. 최소 여행 시간 확인 (너무 빨리 하차하는 것 방지)
        if (!hasMinimumTripTime(state, MIN_TRIP_TIME_SECONDS)) {
            log.debug("⏰ [하차감지] 최소 여행 시간 미충족 - 필요: {}초", MIN_TRIP_TIME_SECONDS);
            return false;
        }

        log.info("✅ [하차감지] 최소 여행 시간 조건 충족 - 최소: {}초", MIN_TRIP_TIME_SECONDS);

        // 2. 거리가 임계값을 초과하면 하차 감지 카운트 증가
        if (distance > AUTO_ALIGHTING_DISTANCE_THRESHOLD) {
            state.incrementAlightingDetectionCount();

            log.info("📍 [하차감지] 하차 거리 임계값 초과 - 사용자: {}, 버스: {}, 거리: {}m, 감지횟수: {}/{}",
                    state.getUserId(), onBus.getBusNumber(),
                    Math.round(distance), state.getAlightingDetectionCount(),
                    CONSECUTIVE_DETECTION_THRESHOLD);

            // 3. 연속 감지 횟수가 임계값을 초과하면 하차 처리
            if (state.getAlightingDetectionCount() >= CONSECUTIVE_DETECTION_THRESHOLD) {
                log.info("🎉 [하차감지] 승객 자동 하차 감지 완료! - 사용자: {}, 버스: {}, 거리: {}m, 감지횟수: {}",
                        state.getUserId(), onBus.getBusNumber(),
                        Math.round(distance), state.getAlightingDetectionCount());

                // 하차 처리
                return processAlighting(state, onBus);
            } else {
                log.info("⏳ [하차감지] 감지 횟수 부족 - 계속 감지 중: {}/{}",
                        state.getAlightingDetectionCount(), CONSECUTIVE_DETECTION_THRESHOLD);
            }
        } else {
            // 버스와 거리가 가까우면 카운트 리셋
            if (state.getAlightingDetectionCount() > 0) {
                log.info("🔄 [하차감지] 버스와 가까워져서 하차 감지 카운트 리셋 - 사용자: {}", state.getUserId());
                state.resetAlightingDetectionCount();
            }
        }

        return false;
    }

    /**
     * 버스가 정차 중인지 확인 (속도 기반)
     */
    private boolean isBusStationary(Bus bus) {
        log.debug("🚏 [버스정차] 버스 정차 여부 확인 - 버스: {} (현재는 항상 true 반환)", bus.getBusNumber());
        // TODO: 향후 버스의 속도 정보를 활용하여 정차 여부 판단
        // 현재는 항상 true 반환하여 탑승 감지를 허용
        return true;
    }

    /**
     * 최소 체류 시간 확인
     */
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

    /**
     * 최소 여행 시간 확인 (너무 빨리 하차하는 것 방지)
     */
    private boolean hasMinimumTripTime(PassengerState state, long minSeconds) {
        if (state.getBoardingTime() == null) {
            log.debug("⏰ [여행시간] 탑승 시간을 모르므로 여행 시간 확인 통과");
            return true; // 탑승 시간을 모르면 통과
        }

        long tripTime = (System.currentTimeMillis() - state.getBoardingTime()) / 1000;
        boolean result = tripTime >= minSeconds;

        log.debug("⏰ [여행시간] 버스 여행 시간 확인 - 사용자: {}, 여행시간: {}초, 최소요구: {}초, 결과: {}",
                state.getUserId(), tripTime, minSeconds, result);

        return result;
    }

    /**
     * 가장 가까운 버스와 거리 찾기 - 성능 최적화
     */
    private BusDistance findClosestBus(PassengerState state, List<Bus> buses) {
        log.debug("🔍 [가까운버스] 가장 가까운 버스 찾기 시작 - 사용자: {}, 버스 수: {}",
                state.getUserId(), buses.size());

        BusDistance closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Bus bus : buses) {
            if (bus.getLocation() == null) {
                log.debug("🚌 [가까운버스] 버스 {}의 위치 정보가 없어서 스킵", bus.getBusNumber());
                continue;
            }

            double distance = calculateDistance(
                    state.getLatitude(), state.getLongitude(),
                    bus.getLocation().getY(), bus.getLocation().getX()
            );

            log.debug("📏 [가까운버스] 버스 {} 거리: {}m", bus.getBusNumber(), Math.round(distance));

            if (distance < minDistance) {
                minDistance = distance;
                closest = new BusDistance(bus, distance);
                log.debug("🎯 [가까운버스] 새로운 최단거리 버스 발견 - 버스: {}, 거리: {}m",
                        bus.getBusNumber(), Math.round(distance));
            }
        }

        if (closest != null) {
            log.info("✅ [가까운버스] 가장 가까운 버스 확정 - 버스: {}, 거리: {}m",
                    closest.bus.getBusNumber(), Math.round(closest.distance));
        } else {
            log.warn("❌ [가까운버스] 위치 정보가 있는 버스가 없음");
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
        log.info("🎫 [탑승처리] 자동 탑승 처리 시작 - 사용자: {}, 버스: {}", state.getUserId(), bus.getBusNumber());

        try {
            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber(bus.getBusNumber());
            boardingDTO.setOrganizationId(state.getOrganizationId());
            boardingDTO.setUserId(state.getUserId());
            boardingDTO.setAction(BusBoardingDTO.BoardingAction.BOARD);
            boardingDTO.setTimestamp(System.currentTimeMillis());

            log.info("📋 [탑승처리] 탑승 DTO 생성 완료 - {}", boardingDTO);
            log.info("🚀 [탑승처리] BusService.processBusBoarding 호출 시작");

            boolean success = busService.processBusBoarding(boardingDTO);

            log.info("🎯 [탑승처리] BusService.processBusBoarding 호출 결과: {}", success);

            if (success) {
                // 탑승 상태 업데이트
                state.setOnBus(true);
                state.setCurrentBusNumber(bus.getBusNumber());
                state.setBoardingTime(System.currentTimeMillis());
                state.resetBoardingDetectionCount();
                state.resetAlightingDetectionCount();

                log.info("🎉 [탑승처리] 승객 자동 탑승 처리 완료! - 사용자: {}, 버스: {}, 현재 승객수: {}",
                        state.getUserId(), bus.getBusNumber(), bus.getOccupiedSeats() + 1);
                return true;
            } else {
                log.warn("❌ [탑승처리] 승객 자동 탑승 처리 실패 - 사용자: {}, 버스: {} (좌석 부족 또는 운행 중지)",
                        state.getUserId(), bus.getBusNumber());
                state.resetBoardingDetectionCount(); // 실패 시 카운트 리셋
                return false;
            }
        } catch (Exception e) {
            log.error("❌ [탑승처리] 자동 탑승 처리 중 오류 발생 - 사용자: {}, 버스: {}, 오류: {}",
                    state.getUserId(), bus.getBusNumber(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 자동 하차 처리
     */
    private boolean processAlighting(PassengerState state, Bus bus) {
        log.info("🚪 [하차처리] 자동 하차 처리 시작 - 사용자: {}, 버스: {}", state.getUserId(), bus.getBusNumber());

        try {
            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber(bus.getBusNumber());
            boardingDTO.setOrganizationId(state.getOrganizationId());
            boardingDTO.setUserId(state.getUserId());
            boardingDTO.setAction(BusBoardingDTO.BoardingAction.ALIGHT);
            boardingDTO.setTimestamp(System.currentTimeMillis());

            log.info("📋 [하차처리] 하차 DTO 생성 완료 - {}", boardingDTO);
            log.info("🚀 [하차처리] BusService.processBusBoarding 호출 시작");

            boolean success = busService.processBusBoarding(boardingDTO);

            log.info("🎯 [하차처리] BusService.processBusBoarding 호출 결과: {}", success);

            if (success) {
                // 하차 상태 업데이트
                state.setOnBus(false);
                state.setCurrentBusNumber(null);
                state.setBoardingTime(null);
                state.resetBoardingDetectionCount();
                state.resetAlightingDetectionCount();

                log.info("🎉 [하차처리] 승객 자동 하차 처리 완료! - 사용자: {}, 버스: {}",
                        state.getUserId(), bus.getBusNumber());
                return true;
            } else {
                log.warn("❌ [하차처리] 승객 자동 하차 처리 실패 - 사용자: {}, 버스: {}",
                        state.getUserId(), bus.getBusNumber());
                state.resetAlightingDetectionCount(); // 실패 시 카운트 리셋
                return false;
            }
        } catch (Exception e) {
            log.error("❌ [하차처리] 자동 하차 처리 중 오류 발생 - 사용자: {}, 버스: {}, 오류: {}",
                    state.getUserId(), bus.getBusNumber(), e.getMessage(), e);
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

            log.info("👤 [승객상태] 새 승객 상태 객체 생성 - 사용자: {}, 조직: {}", userId, organizationId);
        }

        void updateLocation(double latitude, double longitude, long timestamp) {
            log.debug("📍 [승객상태] 위치 업데이트 - 사용자: {}, 위치: ({}, {})",
                    userId, latitude, longitude);

            // 위치가 크게 변경되었으면 locationSetTime 갱신
            if (this.latitude == 0 && this.longitude == 0) {
                // 첫 번째 위치 설정
                this.locationSetTime = timestamp;
                log.info("📍 [승객상태] 첫 번째 위치 설정 - 사용자: {}, 시간: {}",
                        userId, new java.util.Date(timestamp));
            } else {
                double distance = calculateDistanceSimple(this.latitude, this.longitude, latitude, longitude);
                log.debug("📏 [승객상태] 이전 위치와의 거리: {}m", Math.round(distance));

                if (distance > 20) { // 20m 이상 이동시 위치 변경으로 간주
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
            // 간단한 유클리드 거리 계산 (성능 최적화)
            double deltaLat = lat1 - lat2;
            double deltaLon = lon1 - lon2;
            return Math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon) * 111000; // 대략적인 미터 변환
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
}