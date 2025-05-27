package capston2024.bustracker.handler;

import capston2024.bustracker.config.dto.busEtc.BusBoardingDTO;
import capston2024.bustracker.config.dto.busEtc.PassengerLocationDTO;
import capston2024.bustracker.config.dto.realtime.BoardingDetectionResultDTO;
import capston2024.bustracker.config.dto.realtime.BusRealtimeStatusDTO;
import capston2024.bustracker.service.RealtimeLocationService;
import capston2024.bustracker.service.PassengerLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class BusPassengerWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final PassengerLocationService passengerLocationService;
    private final RealtimeLocationService realtimeLocationService;

    // 조직별 승객 세션 관리
    private final Map<String, Set<WebSocketSession>> organizationSessions = new ConcurrentHashMap<>();
    // 세션별 정보 관리
    private final Map<String, SessionInfo> sessionInfoMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("승객 WebSocket 연결: {}", session.getId());

        // 연결 성공 메시지 전송
        try {
            Map<String, Object> welcome = Map.of(
                    "type", "connection",
                    "status", "connected",
                    "message", "WebSocket 연결 성공",
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, welcome);
        } catch (Exception e) {
            log.error("환영 메시지 전송 실패", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        SessionInfo info = sessionInfoMap.remove(sessionId);

        if (info != null) {
            Set<WebSocketSession> sessions = organizationSessions.get(info.organizationId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    organizationSessions.remove(info.organizationId);
                }
            }
            log.info("승객 WebSocket 연결 종료: 세션={}, 조직={}, 사용자={}",
                    sessionId, info.organizationId, info.userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("승객 메시지 수신: {}", payload);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String messageType = (String) data.get("type");

            if (messageType == null) {
                sendError(session, "메시지 타입이 없습니다");
                return;
            }

            // 메시지 타입별 처리
            switch (messageType) {
                case "subscribe" -> handleSubscribe(session, data);
                case "location" -> handlePassengerLocation(session, data);
                case "manualBoarding" -> handleManualBoarding(session, data);
                case "requestStatus" -> handleStatusRequest(session, data);
                case "ping" -> handlePing(session);
                default -> sendError(session, "알 수 없는 메시지 타입: " + messageType);
            }

        } catch (Exception e) {
            log.error("승객 메시지 처리 중 오류", e);
            sendError(session, "메시지 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 구독 처리 (초기 연결 및 설정)
     */
    private void handleSubscribe(WebSocketSession session, Map<String, Object> data) {
        try {
            String organizationId = (String) data.get("organizationId");
            String userId = (String) data.get("userId");

            if (organizationId == null || userId == null) {
                sendError(session, "조직 ID와 사용자 ID가 필요합니다");
                return;
            }

            // 세션 정보 저장
            SessionInfo info = new SessionInfo(organizationId, userId, System.currentTimeMillis());
            sessionInfoMap.put(session.getId(), info);

            // 조직별 세션 관리
            organizationSessions.computeIfAbsent(organizationId, k -> ConcurrentHashMap.newKeySet())
                    .add(session);

            log.info("승객 구독 완료: 조직={}, 사용자={}, 세션={}",
                    organizationId, userId, session.getId());

            // 현재 운행 중인 버스 상태 전송
            List<BusRealtimeStatusDTO> busStatuses = realtimeLocationService
                    .getOrganizationBusStatuses(organizationId);

            Map<String, Object> response = Map.of(
                    "type", "subscribed",
                    "message", "실시간 버스 정보 구독 완료",
                    "currentBuses", busStatuses,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);

        } catch (Exception e) {
            log.error("구독 처리 중 오류", e);
            sendError(session, "구독 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 승객 위치 업데이트 및 자동 탑승 감지
     */
    private void handlePassengerLocation(WebSocketSession session, Map<String, Object> data) {
        try {
            SessionInfo info = sessionInfoMap.get(session.getId());
            if (info == null) {
                sendError(session, "구독 정보가 없습니다. 먼저 subscribe를 호출하세요.");
                return;
            }

            // 위치 데이터 추출
            Map<String, Object> locationData = (Map<String, Object>) data.get("data");
            if (locationData == null) {
                sendError(session, "위치 데이터가 없습니다");
                return;
            }

            PassengerLocationDTO locationDTO = new PassengerLocationDTO();
            locationDTO.setUserId(info.userId);
            locationDTO.setOrganizationId(info.organizationId);
            locationDTO.setLatitude(((Number) locationData.get("latitude")).doubleValue());
            locationDTO.setLongitude(((Number) locationData.get("longitude")).doubleValue());
            locationDTO.setTimestamp(System.currentTimeMillis());

            // 자동 탑승 감지 처리
            BoardingDetectionResultDTO result = passengerLocationService.processPassengerLocation(locationDTO);

            // 위치 업데이트 확인 응답
            Map<String, Object> ack = Map.of(
                    "type", "locationAck",
                    "status", "received",
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, ack);

            // 자동 탑승/하차 감지 시 알림
            if (result != null && result.isAutoDetected() && result.isSuccessful()) {
                Map<String, Object> notification = Map.of(
                        "type", "boardingDetected",
                        "data", result,
                        "timestamp", System.currentTimeMillis()
                );
                sendMessage(session, notification);

                // 다른 승객들에게도 좌석 상태 업데이트 브로드캐스트
                broadcastSeatUpdate(info.organizationId, result.getOperationId());
            }

        } catch (Exception e) {
            log.error("승객 위치 처리 중 오류", e);
            sendError(session, "위치 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 수동 탑승/하차 처리
     */
    private void handleManualBoarding(WebSocketSession session, Map<String, Object> data) {
        try {
            SessionInfo info = sessionInfoMap.get(session.getId());
            if (info == null) {
                sendError(session, "구독 정보가 없습니다");
                return;
            }

            Map<String, Object> boardingData = (Map<String, Object>) data.get("data");
            if (boardingData == null) {
                sendError(session, "탑승 데이터가 없습니다");
                return;
            }

            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber((String) boardingData.get("busNumber"));
            boardingDTO.setOrganizationId(info.organizationId);
            boardingDTO.setUserId(info.userId);
            boardingDTO.setAction(BusBoardingDTO.BoardingAction.valueOf((String) boardingData.get("action")));
            boardingDTO.setTimestamp(System.currentTimeMillis());

            // 수동 탑승/하차 처리
            boolean success = passengerLocationService.processManualBoarding(boardingDTO);

            Map<String, Object> response = Map.of(
                    "type", "boardingResult",
                    "success", success,
                    "message", success ? "탑승/하차 처리 완료" : "탑승/하차 처리 실패",
                    "action", boardingDTO.getAction(),
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);

            // 성공 시 좌석 상태 브로드캐스트
            if (success) {
                broadcastSeatUpdate(info.organizationId, boardingDTO.getBusNumber());
            }

        } catch (Exception e) {
            log.error("수동 탑승 처리 중 오류", e);
            sendError(session, "탑승 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 현재 버스 상태 요청 처리
     */
    private void handleStatusRequest(WebSocketSession session, Map<String, Object> data) {
        try {
            SessionInfo info = sessionInfoMap.get(session.getId());
            if (info == null) {
                sendError(session, "구독 정보가 없습니다");
                return;
            }

            List<BusRealtimeStatusDTO> busStatuses = realtimeLocationService
                    .getOrganizationBusStatuses(info.organizationId);

            Map<String, Object> response = Map.of(
                    "type", "statusUpdate",
                    "buses", busStatuses,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);

        } catch (Exception e) {
            log.error("상태 요청 처리 중 오류", e);
            sendError(session, "상태 조회 중 오류: " + e.getMessage());
        }
    }

    /**
     * 핑 처리 (연결 유지)
     */
    private void handlePing(WebSocketSession session) {
        try {
            Map<String, Object> pong = Map.of(
                    "type", "pong",
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, pong);
        } catch (Exception e) {
            log.error("핑 응답 실패", e);
        }
    }

    /**
     * 실시간 버스 상태 브로드캐스트 (이벤트 리스너)
     */
    @EventListener
    public void handlePassengerBroadcast(RealtimeLocationService.PassengerBroadcastEvent event) {
        Set<WebSocketSession> sessions = organizationSessions.get(event.organizationId());

        if (sessions != null && !sessions.isEmpty()) {
            Map<String, Object> message = Map.of(
                    "type", "busUpdate",
                    "data", event.busStatus(),
                    "timestamp", System.currentTimeMillis()
            );

            // 해당 조직의 모든 승객에게 브로드캐스트
            broadcastToOrganization(event.organizationId(), message);

            log.debug("조직 {}의 {}명 승객에게 버스 상태 브로드캐스트",
                    event.organizationId(), sessions.size());
        }
    }

    /**
     * 좌석 상태 업데이트 브로드캐스트
     */
    private void broadcastSeatUpdate(String organizationId, String busNumberOrOperationId) {
        try {
            // 운행 중인 버스 상태 조회
            List<BusRealtimeStatusDTO> busStatuses = realtimeLocationService
                    .getOrganizationBusStatuses(organizationId);

            // 해당 버스 찾기
            BusRealtimeStatusDTO targetBus = busStatuses.stream()
                    .filter(bus -> bus.getBusNumber().equals(busNumberOrOperationId) ||
                            bus.getOperationId().equals(busNumberOrOperationId))
                    .findFirst()
                    .orElse(null);

            if (targetBus != null) {
                Map<String, Object> seatUpdate = Map.of(
                        "type", "seatUpdate",
                        "busNumber", targetBus.getBusNumber(),
                        "operationId", targetBus.getOperationId(),
                        "totalSeats", targetBus.getTotalSeats(),
                        "currentPassengers", targetBus.getCurrentPassengers(),
                        "availableSeats", targetBus.getAvailableSeats(),
                        "timestamp", System.currentTimeMillis()
                );

                broadcastToOrganization(organizationId, seatUpdate);
            }

        } catch (Exception e) {
            log.error("좌석 상태 브로드캐스트 중 오류", e);
        }
    }

    /**
     * 조직의 모든 세션에 메시지 브로드캐스트
     */
    private void broadcastToOrganization(String organizationId, Object message) {
        Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
        if (sessions != null) {
            List<WebSocketSession> deadSessions = new ArrayList<>();

            sessions.forEach(session -> {
                if (session.isOpen()) {
                    try {
                        sendMessage(session, message);
                    } catch (Exception e) {
                        log.error("브로드캐스트 실패: 세션={}", session.getId(), e);
                        deadSessions.add(session);
                    }
                } else {
                    deadSessions.add(session);
                }
            });

            // 죽은 세션 제거
            deadSessions.forEach(sessions::remove);
        }
    }

    /**
     * 연결 상태 확인 (30초마다)
     */
    @Scheduled(fixedDelay = 30000)
    public void checkConnections() {
        sessionInfoMap.entrySet().removeIf(entry -> {
            SessionInfo info = entry.getValue();
            boolean expired = (System.currentTimeMillis() - info.lastActivity) > 60000; // 1분

            if (expired) {
                log.debug("비활성 세션 제거: {}", entry.getKey());
                // 조직 세션에서도 제거
                Set<WebSocketSession> sessions = organizationSessions.get(info.organizationId);
                if (sessions != null) {
                    sessions.removeIf(s -> s.getId().equals(entry.getKey()));
                }
            }

            return expired;
        });
    }

    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        String jsonMessage = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(jsonMessage));
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> response = Map.of(
                    "type", "error",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
        } catch (Exception e) {
            log.error("에러 메시지 전송 실패", e);
        }
    }

    /**
     * 세션 정보 저장용 내부 클래스
     */
    private static class SessionInfo {
        final String organizationId;
        final String userId;
        long lastActivity;

        SessionInfo(String organizationId, String userId, long lastActivity) {
            this.organizationId = organizationId;
            this.userId = userId;
            this.lastActivity = lastActivity;
        }
    }
}