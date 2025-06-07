package capston2024.bustracker.handler;

import capston2024.bustracker.config.ConnectionLimitInterceptor;
import capston2024.bustracker.config.dto.BusBoardingDTO;
import capston2024.bustracker.config.dto.BusRealTimeStatusDTO;
import capston2024.bustracker.config.dto.PassengerLocationDTO;
import capston2024.bustracker.service.BusService;
import capston2024.bustracker.service.PassengerLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 개선된 승객 앱과의 WebSocket 통신 핸들러
 * - 메모리 누수 방지
 * - 배터리 최적화 고려
 * - 에러 처리 강화
 * - 자동 탑승/하차 감지 개선
 */
@Component
@Slf4j
public class BusPassengerWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    // 조직별 승객 세션 관리
    private final Map<String, Set<WebSocketSession>> organizationSessions = new ConcurrentHashMap<>();
    // 세션 역매핑을 위한 맵
    private final Map<String, String> sessionToOrgMap = new ConcurrentHashMap<>();
    // 세션과 사용자 ID 매핑
    private final Map<String, String> sessionToUserMap = new ConcurrentHashMap<>();
    // 마지막 활동 시간 추적
    private final Map<String, Instant> lastActivityMap = new ConcurrentHashMap<>();

    // 세션 정리를 위한 스케줄러
    private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(1);

    @Autowired
    public BusPassengerWebSocketHandler(ObjectMapper objectMapper, ApplicationContext applicationContext) {
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;

        // 10분마다 비활성 세션 정리
        cleanupScheduler.scheduleAtFixedRate(this::cleanupInactiveSessions, 10, 10, TimeUnit.MINUTES);
    }

    // 지연 초기화를 통해 BusService 얻기 (순환 의존성 방지)
    private BusService getBusService() {
        return applicationContext.getBean(BusService.class);
    }

    // 지연 초기화를 통해 PassengerLocationService 얻기
    private PassengerLocationService getPassengerLocationService() {
        return applicationContext.getBean(PassengerLocationService.class);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");
        log.info("승객 WebSocket 연결 설정: 세션 ID = {}, IP = {}", session.getId(), clientIp);

        // 활동 시간 초기화
        lastActivityMap.put(session.getId(), Instant.now());

        // 연결 성공 메시지 전송
        try {
            sendMessage(session, Map.of(
                    "type", "connection_established",
                    "status", "success",
                    "message", "웹소켓 연결이 성공적으로 설정되었습니다.",
                    "instructions", "조직 ID와 함께 subscribe 메시지를 보내주세요.",
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("연결 확인 메시지 전송 실패: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String organizationId = sessionToOrgMap.remove(sessionId);
        String userId = sessionToUserMap.remove(sessionId);
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");

        if (organizationId != null) {
            Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
            if (sessions != null) {
                sessions.remove(session);
                // 조직에 세션이 없으면 맵에서 제거
                if (sessions.isEmpty()) {
                    organizationSessions.remove(organizationId);
                }
            }
            log.info("승객 WebSocket 연결 종료: 세션 ID = {}, 조직 ID = {}, 사용자 ID = {}, 상태 = {}",
                    sessionId, organizationId, userId, status.getCode());
        } else {
            log.info("승객 WebSocket 연결 종료: 세션 ID = {}, 상태 = {}", sessionId, status.getCode());
        }

        // 활동 시간 정보 제거
        lastActivityMap.remove(sessionId);

        // IP별 연결 수 감소
        if (clientIp != null) {
            ConnectionLimitInterceptor.decrementConnection(clientIp);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("승객으로부터 메시지 수신: 세션 ID = {}, 메시지 길이 = {}",
                session.getId(), payload.length());

        // 활동 시간 업데이트
        lastActivityMap.put(session.getId(), Instant.now());

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String messageType = (String) data.get("type");
            String organizationId = (String) data.get("organizationId");

            // 기본 검증
            if (messageType == null) {
                sendErrorMessage(session, "메시지 타입이 필요합니다.");
                return;
            }

            // 조직 ID가 필요한 메시지 타입들
            if (needsOrganizationId(messageType) && (organizationId == null || organizationId.isEmpty())) {
                sendErrorMessage(session, "조직 ID가 필요합니다.");
                return;
            }

            // 세션 맵핑 등록 (처음 메시지를 보낼 때)
            if (organizationId != null && !sessionToOrgMap.containsKey(session.getId())) {
                registerSession(session, organizationId);
            }

            // 메시지 타입에 따른 처리
            switch (messageType) {
                case "subscribe":
                    handleSubscribeMessage(session, data);
                    break;
                case "boarding":
                    handleBoardingMessage(session, data);
                    break;
                case "location":
                    handleLocationMessage(session, data);
                    break;
                case "heartbeat":
                    handleHeartbeat(session);
                    break;
                case "get_bus_status":
                    handleGetBusStatus(session, data);
                    break;
                default:
                    sendErrorMessage(session, "알 수 없는 메시지 타입: " + messageType);
            }

        } catch (Exception e) {
            log.error("승객 메시지 처리 중 오류 발생: 세션 ID = {}, 오류 = {}",
                    session.getId(), e.getMessage(), e);
            sendErrorMessage(session, "메시지 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private boolean needsOrganizationId(String messageType) {
        return !"heartbeat".equals(messageType);
    }

    private void registerSession(WebSocketSession session, String organizationId) {
        sessionToOrgMap.put(session.getId(), organizationId);
        organizationSessions.computeIfAbsent(organizationId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
        log.info("승객 세션 등록: 조직 ID = {}, 세션 ID = {}", organizationId, session.getId());
    }

    /**
     * 승객 위치 메시지 처리 - 배터리 최적화 고려
     */
    private void handleLocationMessage(WebSocketSession session, Map<String, Object> data) {
        try {
            Map<String, Object> locationData = (Map<String, Object>) data.get("data");
            if (locationData == null) {
                sendErrorMessage(session, "위치 데이터가 필요합니다.");
                return;
            }

            // 데이터 추출 및 검증
            String userId = (String) locationData.get("userId");
            Double latitude = getDoubleValue(locationData.get("latitude"));
            Double longitude = getDoubleValue(locationData.get("longitude"));

            if (userId == null || latitude == null || longitude == null) {
                sendErrorMessage(session, "사용자 ID, 위도, 경도가 필요합니다.");
                return;
            }

            // GPS 좌표 유효성 검증
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                sendErrorMessage(session, "유효하지 않은 GPS 좌표입니다.");
                return;
            }

            PassengerLocationDTO locationDTO = new PassengerLocationDTO();
            locationDTO.setUserId(userId);
            locationDTO.setOrganizationId((String) data.get("organizationId"));
            locationDTO.setLatitude(latitude);
            locationDTO.setLongitude(longitude);
            locationDTO.setTimestamp(System.currentTimeMillis());

            // 사용자 ID 저장
            sessionToUserMap.put(session.getId(), userId);

            // 위치 처리 서비스 호출 (배터리 최적화 포함)
            boolean boardingDetected = getPassengerLocationService().processPassengerLocation(locationDTO);

            if (boardingDetected) {
                // 자동 탑승 감지 시 클라이언트에 알림
                sendSuccessMessage(session, "버스 탑승/하차가 자동으로 감지되었습니다.");
            } else {
                // 일반적인 위치 업데이트 확인
                sendMessage(session, Map.of(
                        "type", "location_processed",
                        "status", "success",
                        "timestamp", System.currentTimeMillis()
                ));
            }

        } catch (Exception e) {
            log.error("위치 메시지 처리 중 오류 발생: {}", e.getMessage());
            sendErrorMessage(session, "위치 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private Double getDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = sessionToUserMap.get(session.getId());
        String organizationId = sessionToOrgMap.get(session.getId());

        log.error("승객 WebSocket 통신 오류: 세션 ID = {}, 사용자 ID = {}, 조직 ID = {}, 오류 = {}",
                session.getId(), userId, organizationId, exception.getMessage(), exception);

        // 오류 발생 시 세션 정리
        try {
            session.close();
        } catch (Exception e) {
            log.error("세션 강제 종료 실패: {}", e.getMessage());
        }
    }

    /**
     * 특정 조직의 모든 승객에게 버스 상태 업데이트 전송
     * - 네트워크 효율성을 위한 배치 전송
     */
    public void broadcastBusStatus(String organizationId, BusRealTimeStatusDTO busStatus) {
        Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
        if (sessions != null && !sessions.isEmpty()) {
            Map<String, Object> message = Map.of(
                    "type", "busUpdate",
                    "data", busStatus,
                    "timestamp", System.currentTimeMillis()
            );

            // 병렬 처리로 성능 향상
            sessions.parallelStream().forEach(session -> {
                if (session.isOpen()) {
                    try {
                        sendMessage(session, message);
                    } catch (Exception e) {
                        log.error("승객에게 버스 상태 업데이트 전송 중 오류 발생: 세션 ID = {}, 오류 = {}",
                                session.getId(), e.getMessage());

                        // 전송 실패한 세션은 정리 대상으로 표시
                        markSessionForCleanup(session);
                    }
                }
            });

            log.debug("조직 {}의 {}명의 승객에게 버스 상태 업데이트 전송", organizationId, sessions.size());
        }
    }

    /**
     * 조직별 활성 승객 수 조회
     */
    public int getActivePassengerCount(String organizationId) {
        Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
        if (sessions == null) return 0;

        // 유효한 세션만 카운트
        return (int) sessions.stream()
                .filter(session -> session != null && session.isOpen())
                .count();
    }

    /**
     * 전체 활성 승객 수 조회
     */
    public int getTotalActivePassengerCount() {
        return organizationSessions.values().stream()
                .mapToInt(sessions -> (int) sessions.stream()
                        .filter(session -> session != null && session.isOpen())
                        .count())
                .sum();
    }

    /**
     * 구독 메시지 처리 - 초기 데이터 제공
     */
    private void handleSubscribeMessage(WebSocketSession session, Map<String, Object> data) {
        String organizationId = (String) data.get("organizationId");

        try {
            // 해당 조직의 모든 버스 상태 즉시 전송
            getBusService().getAllBusStatusByOrganizationId(organizationId).forEach(busStatus -> {
                try {
                    Map<String, Object> message = Map.of(
                            "type", "busUpdate",
                            "data", busStatus,
                            "timestamp", System.currentTimeMillis()
                    );
                    sendMessage(session, message);
                } catch (Exception e) {
                    log.error("초기 버스 상태 전송 중 오류 발생: {}", e.getMessage());
                }
            });

            sendSuccessMessage(session, "구독이 성공적으로 등록되었습니다.");

        } catch (Exception e) {
            log.error("구독 처리 중 오류 발생: {}", e.getMessage());
            sendErrorMessage(session, "구독 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 승객 탑승/하차 메시지 처리
     */
    private void handleBoardingMessage(WebSocketSession session, Map<String, Object> data) {
        try {
            Map<String, Object> boardingData = (Map<String, Object>) data.get("data");
            if (boardingData == null) {
                sendErrorMessage(session, "탑승/하차 데이터가 필요합니다.");
                return;
            }

            String busNumber = (String) boardingData.get("busNumber");
            String userId = (String) boardingData.get("userId");
            String actionStr = (String) boardingData.get("action");

            if (busNumber == null || userId == null || actionStr == null) {
                sendErrorMessage(session, "버스 번호, 사용자 ID, 액션이 필요합니다.");
                return;
            }

            BusBoardingDTO boardingDTO = new BusBoardingDTO();
            boardingDTO.setBusNumber(busNumber);
            boardingDTO.setOrganizationId((String) data.get("organizationId"));
            boardingDTO.setUserId(userId);

            try {
                boardingDTO.setAction(BusBoardingDTO.BoardingAction.valueOf(actionStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                sendErrorMessage(session, "유효하지 않은 액션입니다. BOARD 또는 ALIGHT를 사용하세요.");
                return;
            }

            boardingDTO.setTimestamp(System.currentTimeMillis());

            boolean success = getBusService().processBusBoarding(boardingDTO);

            if (success) {
                String message = boardingDTO.getAction() == BusBoardingDTO.BoardingAction.BOARD ?
                        "탑승이 성공적으로 처리되었습니다." : "하차가 성공적으로 처리되었습니다.";
                sendSuccessMessage(session, message);
            } else {
                sendErrorMessage(session, "탑승/하차 처리에 실패했습니다. 버스 상태를 확인해주세요.");
            }

        } catch (Exception e) {
            log.error("탑승/하차 메시지 처리 중 오류 발생: {}", e.getMessage());
            sendErrorMessage(session, "탑승/하차 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 하트비트 처리
     */
    private void handleHeartbeat(WebSocketSession session) {
        try {
            sendMessage(session, Map.of(
                    "type", "heartbeat_response",
                    "status", "alive",
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("하트비트 응답 전송 실패: {}", e.getMessage());
        }
    }

    /**
     * 버스 상태 조회 처리
     */
    private void handleGetBusStatus(WebSocketSession session, Map<String, Object> data) {
        String organizationId = (String) data.get("organizationId");
        String busNumber = (String) data.get("busNumber");

        try {
            if (busNumber != null) {
                // 특정 버스 상태 조회
                // 구현 필요: BusService에서 특정 버스 상태 조회 메서드 추가
                sendErrorMessage(session, "특정 버스 상태 조회는 아직 구현되지 않았습니다.");
            } else {
                // 전체 버스 상태 조회
                handleSubscribeMessage(session, data);
            }
        } catch (Exception e) {
            log.error("버스 상태 조회 중 오류 발생: {}", e.getMessage());
            sendErrorMessage(session, "버스 상태 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 성공 메시지 전송
     */
    private void sendSuccessMessage(WebSocketSession session, String message) {
        try {
            Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", message,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
        } catch (Exception e) {
            log.error("성공 메시지 전송 중 오류 발생: {}", e.getMessage());
        }
    }

    /**
     * 오류 메시지 전송
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> response = Map.of(
                    "status", "error",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
        } catch (Exception e) {
            log.error("오류 메시지 전송 중 오류 발생: {}", e.getMessage());
        }
    }

    /**
     * 세션에 메시지 전송 (내부 헬퍼 메서드)
     */
    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (session != null && session.isOpen()) {
            String jsonMessage = objectMapper.writeValueAsString(message);
            synchronized (session) { // 동시성 문제 방지
                session.sendMessage(new TextMessage(jsonMessage));
            }
        }
    }

    /**
     * 비활성 세션들 정리 (10분 이상 비활성)
     */
    private void cleanupInactiveSessions() {
        Instant threshold = Instant.now().minusSeconds(600); // 10분 임계값

        lastActivityMap.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            Instant lastActivity = entry.getValue();

            if (lastActivity.isBefore(threshold)) {
                log.info("비활성 세션 정리: 세션 ID = {}", sessionId);

                // 세션 정리
                String organizationId = sessionToOrgMap.remove(sessionId);
                String userId = sessionToUserMap.remove(sessionId);

                if (organizationId != null) {
                    Set<WebSocketSession> sessions = organizationSessions.get(organizationId);
                    if (sessions != null) {
                        sessions.removeIf(session -> session.getId().equals(sessionId));
                        if (sessions.isEmpty()) {
                            organizationSessions.remove(organizationId);
                        }
                    }
                }

                return true; // 맵에서 제거
            }

            return false;
        });
    }

    /**
     * 문제 있는 세션을 정리 대상으로 표시
     */
    private void markSessionForCleanup(WebSocketSession session) {
        // 즉시 정리하지 않고 다음 정리 주기에서 처리되도록 활동 시간을 오래 전으로 설정
        lastActivityMap.put(session.getId(), Instant.now().minusSeconds(700));
    }

    /**
     * 셧다운 훅 - 애플리케이션 종료 시 리소스 정리
     */
    @PreDestroy
    public void shutdown() {
        log.info("BusPassengerWebSocketHandler 종료 중...");

        // 모든 세션 정리
        organizationSessions.values().forEach(sessions -> {
            sessions.forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.close(CloseStatus.GOING_AWAY);
                    }
                } catch (Exception e) {
                    log.error("세션 종료 중 오류: {}", e.getMessage());
                }
            });
        });

        // 스케줄러 종료
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("BusPassengerWebSocketHandler 종료 완료");
    }
}
