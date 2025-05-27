package capston2024.bustracker.service;

import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.domain.BusTrackingEvent;
import capston2024.bustracker.repository.BusOperationRepository;
import capston2024.bustracker.repository.BusTrackingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RealtimeSchedulerService {

    private final BusOperationRepository busOperationRepository;
    private final BusTrackingEventRepository busTrackingEventRepository;
    private final RealtimeLocationService realtimeLocationService;
    private final PassengerLocationService passengerLocationService;

    /**
     * 만료된 운행 자동 종료 (1시간마다 실행)
     */
    @Scheduled(cron = "0 0 * * * *") // 매시간 정각
    @Transactional
    public void closeExpiredOperations() {
        log.info("만료된 운행 자동 종료 작업 시작");

        LocalDateTime now = LocalDateTime.now();

        // 예정 종료시간이 지났지만 아직 완료되지 않은 운행 찾기
        List<BusOperation> expiredOperations = busOperationRepository
                .findByScheduledEndBeforeAndStatusIn(now,
                        List.of(BusOperation.OperationStatus.IN_PROGRESS,
                                BusOperation.OperationStatus.SCHEDULED));

        for (BusOperation operation : expiredOperations) {
            try {
                // 마지막 업데이트 시간 확인 (2시간 이상 업데이트 없으면 자동 종료)
                if (operation.getUpdatedAt() != null &&
                        operation.getUpdatedAt().isBefore(now.minusHours(2))) {

                    operation.setStatus(BusOperation.OperationStatus.COMPLETED);
                    operation.setActualEnd(now);
                    busOperationRepository.save(operation);

                    // 실시간 위치 캐시 제거
                    realtimeLocationService.removeOperationLocation(operation.getOperationId());

                    log.info("만료된 운행 자동 종료: {}", operation.getOperationId());
                }
            } catch (Exception e) {
                log.error("운행 {} 자동 종료 중 오류", operation.getOperationId(), e);
            }
        }

        log.info("만료된 운행 자동 종료 작업 완료: {}건 처리", expiredOperations.size());
    }

    /**
     * 오래된 이벤트 정리 (매일 새벽 2시 실행)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldEvents() {
        log.info("오래된 이벤트 정리 작업 시작");

        // 30일 이상 된 이벤트 삭제
        Instant cutoffTime = Instant.now().minus(30, ChronoUnit.DAYS);
        long deletedCount = busTrackingEventRepository.deleteByTimestampBefore(cutoffTime);

        log.info("오래된 이벤트 정리 완료: {}건 삭제", deletedCount);
    }

    /**
     * 시스템 상태 모니터링 (5분마다 실행)
     */
    @Scheduled(fixedDelay = 300000) // 5분
    public void monitorSystemStatus() {
        try {
            // 현재 활성 운행 수
            long activeOperations = busOperationRepository.countByStatus(
                    BusOperation.OperationStatus.IN_PROGRESS);

            // 최근 1시간 이벤트 수
            Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
            long recentEvents = busTrackingEventRepository.countByTimestampAfter(oneHourAgo);

            log.info("시스템 상태 - 활성 운행: {}건, 최근 1시간 이벤트: {}건",
                    activeOperations, recentEvents);

            // 이상 징후 감지
            if (activeOperations > 100) {
                log.warn("비정상적으로 많은 활성 운행 감지: {}건", activeOperations);
            }

            if (recentEvents > 10000) {
                log.warn("과도한 이벤트 발생 감지: {}건/시간", recentEvents);
            }

        } catch (Exception e) {
            log.error("시스템 상태 모니터링 중 오류", e);
        }
    }

    /**
     * 운행 통계 업데이트 (매일 자정 실행)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void updateOperationStatistics() {
        log.info("운행 통계 업데이트 작업 시작");

        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            LocalDateTime today = LocalDateTime.now();

            // 어제 완료된 운행들
            List<BusOperation> completedOperations = busOperationRepository
                    .findByStatusAndActualEndBetween(
                            BusOperation.OperationStatus.COMPLETED,
                            yesterday, today);

            // 통계 계산
            int totalOperations = completedOperations.size();
            int totalPassengers = completedOperations.stream()
                    .mapToInt(op -> op.getTotalPassengers() != null ? op.getTotalPassengers() : 0)
                    .sum();

            log.info("일일 운행 통계 - 총 운행: {}건, 총 승객: {}명",
                    totalOperations, totalPassengers);

            // TODO: 통계 데이터 저장 또는 리포트 생성

        } catch (Exception e) {
            log.error("운행 통계 업데이트 중 오류", e);
        }
    }

    /**
     * 실시간 위치 캐시 정리 (10분마다 실행)
     */
    @Scheduled(fixedDelay = 600000) // 10분
    public void cleanupLocationCache() {
        log.debug("실시간 위치 캐시 정리 시작");

        try {
            // RealtimeLocationService의 캐시 정리
            realtimeLocationService.cleanupExpiredLocations();

            // PassengerLocationService의 승객 상태 정리
            passengerLocationService.cleanupExpiredPassengerStates();

        } catch (Exception e) {
            log.error("캐시 정리 중 오류", e);
        }
    }
}