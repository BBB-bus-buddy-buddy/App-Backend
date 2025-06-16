package capston2024.bustracker.handler;

import capston2024.bustracker.service.BusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 버스 상태 업데이트 이벤트를 처리하여 승객들에게 실시간으로 전송하는 리스너
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusStatusEventListener {

    private final BusPassengerWebSocketHandler busPassengerWebSocketHandler;

    /**
     * 버스 상태 업데이트 이벤트 처리
     * - 버스 기사가 위치 업데이트를 보낼 때
     * - 승객이 탑승/하차할 때
     * - 버스 정보가 변경될 때
     */
    @EventListener
    @Async  // 비동기 처리로 성능 향상
    public void handleBusStatusUpdate(BusService.BusStatusUpdateEvent event) {
        try {
            log.debug("버스 상태 업데이트 이벤트 수신: 조직={}, 버스={}",
                    event.organizationId(), event.busStatus().getBusNumber());

            // 해당 조직의 모든 승객에게 브로드캐스트
            busPassengerWebSocketHandler.broadcastBusStatus(
                    event.organizationId(),
                    event.busStatus()
            );

            log.debug("버스 상태 업데이트 브로드캐스트 완료: 버스={}",
                    event.busStatus().getBusNumber());

        } catch (Exception e) {
            log.error("버스 상태 업데이트 이벤트 처리 중 오류 발생: 조직={}, 버스={}, 오류={}",
                    event.organizationId(),
                    event.busStatus().getBusNumber(),
                    e.getMessage(), e);
        }
    }
}