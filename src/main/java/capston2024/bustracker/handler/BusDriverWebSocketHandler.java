package capston2024.bustracker.handler;

import capston2024.bustracker.config.ConnectionLimitInterceptor;
import capston2024.bustracker.config.dto.BusRealTimeLocationDTO;
import capston2024.bustracker.service.BusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 개선된 버스 기사 앱과의 WebSocket 통신 핸들러
 * - WebSocket으로 받은 위치 정보를 BusService로 전달
 * - BusService.flushLocationUpdates()가 주기적으로 DB에 반영
 * - 메모리 누수 방지
 * - 에러 처리 강화
 * - 하트비트 추가
 * - 성능 최적화
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BusDriverWebSocketHandler extends TextWebSocketHandler {

    private final BusService busService;
    private final ObjectMapper objectMapper;

    // 세션 관리를 위한 맵들 - 메모리 누수 방지를 위해 ConcurrentHashMap 사용
    private final Map<String, WebSocketSession> driverSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToBusMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToOrganizationMap = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHeartbeatMap = new ConcurrentHashMap<>();

    // 실시간 위치 추적을 위한 추가 맵
    private final Map<String, BusRealTimeLocationDTO> lastKnownLocations = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastLocationUpdateTime = new ConcurrentHashMap<>();

    // 하트비트 체크를 위한 스케줄러
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2);

    // 통계 정보
    private long totalMessagesReceived = 0;
    private long totalLocationUpdates = 0;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");

        log.info("🚌 ================== WebSocket 연결 설정 ==================");
        log.info("🚌 세션 ID: {}", session.getId());
        log.info("🚌 클라이언트 IP: {}", clientIp);
        log.info("🚌 현재 활성 버스 기사 수: {}", driverSessions.size());
        log.info("🚌 ========================================================");

        // 하트비트 초기화
        lastHeartbeatMap.put(session.getId(), Instant.now());

        // 연결 성공 메시지 전송
        try {
            sendMessage(session, Map.of(
                    "type", "connection_established",
                    "status", "success",
                    "message", "웹소켓 연결이 성공적으로 설정되었습니다.",
                    "timestamp", System.currentTimeMillis()
            ));
            log.info("✅ 연결 확인 메시지 전송 완료");
        } catch (Exception e) {
            log.error("❌ 연결 확인 메시지 전송 실패: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String busNumber = sessionToBusMap.remove(sessionId);
        String organizationId = sessionToOrganizationMap.remove(sessionId);
        String clientIp = (String) session.getAttributes().get("CLIENT_IP");

        log.warn("🚌 ================== WebSocket 연결 종료 ==================");
        log.warn("🚌 세션 ID: {}", sessionId);
        log.warn("🚌 버스 번호: {}", busNumber != null ? busNumber : "미등록");
        log.warn("🚌 조직 ID: {}", organizationId != null ? organizationId : "미등록");
        log.warn("🚌 종료 상태: {} - {}", status.getCode(), status.getReason());
        log.warn("🚌 남은 활성 버스: {}", driverSessions.size() - (busNumber != null ? 1 : 0));
        log.warn("🚌 ========================================================");

        // 모든 맵에서 세션 정보 제거 (메모리 누수 방지)
        if (busNumber != null) {
            driverSessions.remove(busNumber);
            lastKnownLocations.remove(busNumber);
            lastLocationUpdateTime.remove(busNumber);
        }

        // 하트비트 정보 제거
        lastHeartbeatMap.remove(sessionId);

        // IP별 연결 수 감소
        if (clientIp != null) {
            ConnectionLimitInterceptor.decrementConnection(clientIp);
        }

        // 버스 상태를 비활성으로 업데이트
        if (busNumber != null) {
            try {
                busService.updateBusInactiveStatus(busNumber);
                log.info("🔴 버스 {} 비활성 상태로 변경됨", busNumber);
            } catch (Exception e) {
                log.error("❌ 버스 비활성 상태 업데이트 실패: {}", e.getMessage());
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        totalMessagesReceived++;

        log.info("📨 ============= WebSocket 메시지 수신 #{} =============", totalMessagesReceived);
        log.info("📨 세션 ID: {}", session.getId());
        log.info("📨 메시지 크기: {} bytes", payload.length());
        log.info("📨 메시지 내용: {}", payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);

        try {
            // 메시지 타입 판별
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            String messageType = (String) messageData.get("type");

            log.info("📨 메시지 타입: {}", messageType != null ? messageType : "LEGACY");
            log.info("📨 =====================================================");

            // 하트비트 업데이트
            lastHeartbeatMap.put(session.getId(), Instant.now());

            if (messageType != null) {
                switch (messageType) {
                    case "location_update":
                        handleLocationUpdate(session, messageData);
                        break;
                    case "heartbeat":
                        handleHeartbeat(session);
                        break;
                    case "bus_status_update":
                        handleBusStatusUpdate(session, messageData);
                        break;
                    default:
                        log.warn("⚠️ 알 수 없는 메시지 타입: {}", messageType);
                        // 기존 로직 (하위 호환성)
                        handleLegacyLocationUpdate(session, payload);
                }
            } else {
                // type 필드가 없는 경우 레거시 처리
                log.info("📨 레거시 메시지 형식 감지");
                handleLegacyLocationUpdate(session, payload);
            }

        } catch (Exception e) {
            log.error("❌ ============= 메시지 처리 오류 =============");
            log.error("❌ 세션 ID: {}", session.getId());
            log.error("❌ 오류: {}", e.getMessage());
            log.error("❌ 스택 트레이스:", e);
            log.error("❌ =========================================");

            sendErrorMessage(session, "메시지 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 위치 업데이트 처리 - 개선된 버전
     */
    private void handleLocationUpdate(WebSocketSession session, Map<String, Object> messageData) {
        try {
            log.info("📍 ========== 위치 업데이트 처리 시작 ==========");

            // 데이터 추출 및 검증
            String busNumber = (String) messageData.get("busNumber");
            String organizationId = (String) messageData.get("organizationId");

            // 숫자 타입 안전하게 처리
            Double latitude = getDoubleValue(messageData.get("latitude"));
            Double longitude = getDoubleValue(messageData.get("longitude"));
            Integer occupiedSeats = getIntegerValue(messageData.get("occupiedSeats"));
            Long timestamp = getLongValue(messageData.get("timestamp"));

            log.info("📍 버스 번호: {}", busNumber);
            log.info("📍 조직 ID: {}", organizationId);
            log.info("📍 위치: ({}, {})", latitude, longitude);
            log.info("📍 승객 수: {}", occupiedSeats);

            // 기본 검증
            if (busNumber == null || organizationId == null ||
                    latitude == null || longitude == null || occupiedSeats == null) {
                log.error("❌ 필수 필드 누락! busNumber: {}, organizationId: {}, lat: {}, lng: {}, seats: {}",
                        busNumber, organizationId, latitude, longitude, occupiedSeats);
                throw new IllegalArgumentException("필수 필드가 누락되었습니다");
            }

            // 위치 유효성 검증
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                log.error("❌ 유효하지 않은 GPS 좌표: ({}, {})", latitude, longitude);
                throw new IllegalArgumentException("유효하지 않은 GPS 좌표입니다");
            }

            // (0, 0) 위치 필터링 추가
            if (latitude == 0.0 && longitude == 0.0) {
                log.warn("⚠️ (0, 0) 위치 수신됨 - 무시합니다. 버스: {}", busNumber);
                sendErrorMessage(session, "유효한 GPS 위치를 기다리는 중입니다.");
                return; // 처리하지 않고 종료
            }

            // 한국 좌표 범위 확인 (선택적 검증)
            if (latitude < 33.0 || latitude > 39.0 || longitude < 124.0 || longitude > 132.0) {
                log.warn("⚠️ 한국 범위 밖의 좌표 수신: ({}, {}), 버스: {}", latitude, longitude, busNumber);
                // 경고만 하고 처리는 계속 (해외 테스트 등을 고려)
            }

            // DTO 생성
            BusRealTimeLocationDTO locationUpdate = new BusRealTimeLocationDTO(
                    busNumber, organizationId, latitude, longitude, occupiedSeats,
                    timestamp != null ? timestamp : System.currentTimeMillis()
            );

            // 세션 맵핑 등록 (처음 메시지를 보낼 때)
            if (!sessionToBusMap.containsKey(session.getId())) {
                sessionToBusMap.put(session.getId(), busNumber);
                sessionToOrganizationMap.put(session.getId(), organizationId);
                driverSessions.put(busNumber, session);

                log.info("🆕 ========== 새로운 버스 기사 등록 ==========");
                log.info("🆕 버스 번호: {}", busNumber);
                log.info("🆕 조직 ID: {}", organizationId);
                log.info("🆕 세션 ID: {}", session.getId());
                log.info("🆕 현재 활성 버스 수: {}", driverSessions.size());
                log.info("🆕 ========================================");
            }

            // 실시간 위치 정보 저장 (승객 탑승 감지용)
            lastKnownLocations.put(busNumber, locationUpdate);
            lastLocationUpdateTime.put(busNumber, Instant.now());

            // 위치 변화 감지 및 로깅
            BusRealTimeLocationDTO previousLocation = lastKnownLocations.get(busNumber);
            if (previousLocation != null) {
                double distance = calculateDistance(
                        previousLocation.getLatitude(), previousLocation.getLongitude(),
                        latitude, longitude
                );

                if (distance > 5) { // 5미터 이상 이동시
                    log.info("🚍 버스 {} 이동 감지: {}m 이동", busNumber, Math.round(distance));
                }
            }

            // BusService로 위치 업데이트 전달
            busService.updateBusLocation(locationUpdate);

            totalLocationUpdates++;

            log.info("✅ 위치 업데이트 #{} 완료", totalLocationUpdates);
            log.info("📍 누적 위치 업데이트: {}", totalLocationUpdates);
            log.info("📍 ==========================================");

            // 성공 응답
            sendSuccessMessage(session, "위치 업데이트가 성공적으로 처리되었습니다.");

        } catch (Exception e) {
            log.error("❌ 위치 업데이트 처리 실패: {}", e.getMessage());
            sendErrorMessage(session, "위치 업데이트 처리 실패: " + e.getMessage());
        }
    }

    /**
     * 레거시 위치 업데이트 처리
     */
    private void handleLegacyLocationUpdate(WebSocketSession session, String payload) throws Exception {
        log.info("🔄 ========== 레거시 위치 업데이트 처리 ==========");

        try {
            // 기존 로직 유지 (하위 호환성)
            BusRealTimeLocationDTO locationUpdate = objectMapper.readValue(payload, BusRealTimeLocationDTO.class);
            String busNumber = locationUpdate.getBusNumber();
            String organizationId = locationUpdate.getOrganizationId();

            log.info("🔄 버스 번호: {}", busNumber);
            log.info("🔄 조직 ID: {}", organizationId);
            log.info("🔄 위치: ({}, {})", locationUpdate.getLatitude(), locationUpdate.getLongitude());
            log.info("🔄 승객 수: {}", locationUpdate.getOccupiedSeats());

            // (0, 0) 위치 필터링 추가
            if (locationUpdate.getLatitude() == 0.0 && locationUpdate.getLongitude() == 0.0) {
                log.warn("⚠️ 레거시 메시지에서 (0, 0) 위치 수신됨 - 무시합니다. 버스: {}", busNumber);
                sendErrorMessage(session, "유효한 GPS 위치를 기다리는 중입니다.");
                return; // 처리하지 않고 종료
            }

            // 세션 맵핑 등록 (처음 메시지를 보낼 때)
            if (!sessionToBusMap.containsKey(session.getId())) {
                sessionToBusMap.put(session.getId(), busNumber);
                sessionToOrganizationMap.put(session.getId(), organizationId);
                driverSessions.put(busNumber, session);

                log.info("🆕 레거시 버스 기사 등록: 버스 {}, 조직 {}", busNumber, organizationId);
            }

            // 실시간 위치 정보 저장
            lastKnownLocations.put(busNumber, locationUpdate);
            lastLocationUpdateTime.put(busNumber, Instant.now());

            // BusService로 위치 업데이트 전달
            busService.updateBusLocation(locationUpdate);

            totalLocationUpdates++;

            log.info("✅ 레거시 위치 업데이트 #{} 완료", totalLocationUpdates);
            log.info("🔄 ============================================");

            // 성공 응답
            sendSuccessMessage(session, "위치 업데이트가 성공적으로 처리되었습니다.");

        } catch (Exception e) {
            log.error("❌ 레거시 위치 업데이트 처리 실패: {}", e.getMessage());
            throw e;
        }
    }

    private void handleHeartbeat(WebSocketSession session) {
        log.debug("💓 하트비트 수신 - 세션 ID: {}", session.getId());
        try {
            sendMessage(session, Map.of(
                    "type", "heartbeat_response",
                    "status", "alive",
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("❌ 하트비트 응답 전송 실패: {}", e.getMessage());
        }
    }

    private void handleBusStatusUpdate(WebSocketSession session, Map<String, Object> messageData) {
        // 향후 확장을 위한 메서드
        log.info("🔄 버스 상태 업데이트 수신: {}", messageData);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String busNumber = sessionToBusMap.get(session.getId());

        log.error("❌ ========== WebSocket 통신 오류 ==========");
        log.error("❌ 세션 ID: {}", session.getId());
        log.error("❌ 버스 번호: {}", busNumber != null ? busNumber : "미등록");
        log.error("❌ 오류 메시지: {}", exception.getMessage());
        log.error("❌ 스택 트레이스:", exception);
        log.error("❌ ======================================");

        // 오류 발생 시 세션 정리
        try {
            session.close();
            log.info("🔴 오류로 인한 세션 종료");
        } catch (Exception e) {
            log.error("❌ 세션 강제 종료 실패: {}", e.getMessage());
        }
    }

    /**
     * 특정 버스 기사에게 메시지 전송
     */
    public void sendMessageToBusDriver(String busNumber, Object message) {
        WebSocketSession session = driverSessions.get(busNumber);
        if (session != null && session.isOpen()) {
            try {
                sendMessage(session, message);
                log.debug("📤 버스 {}에게 메시지 전송 성공", busNumber);
            } catch (Exception e) {
                log.error("❌ 버스 {}에게 메시지 전송 실패: {}", busNumber, e.getMessage());
                // 세션이 유효하지 않으면 정리
                cleanupSession(session.getId(), busNumber);
            }
        } else {
            log.warn("⚠️ 버스 {}의 세션이 유효하지 않습니다", busNumber);
            if (session != null) {
                cleanupSession(session.getId(), busNumber);
            }
        }
    }

    /**
     * 활성화된 버스 기사 수 조회
     */
    public int getActiveBusDriverCount() {
        // 유효하지 않은 세션 제거 후 반환
        cleanupInvalidSessions();
        return driverSessions.size();
    }

    /**
     * 활성화된 버스 번호 목록 조회
     */
    public Set<String> getActiveBusNumbers() {
        cleanupInvalidSessions();
        return new HashSet<>(driverSessions.keySet());
    }

    /**
     * 버스의 마지막 알려진 위치 조회
     */
    public BusRealTimeLocationDTO getLastKnownLocation(String busNumber) {
        return lastKnownLocations.get(busNumber);
    }

    /**
     * 모든 버스의 실시간 위치 조회
     */
    public Map<String, BusRealTimeLocationDTO> getAllBusLocations() {
        return new ConcurrentHashMap<>(lastKnownLocations);
    }

    /**
     * 통계 정보 조회
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
                "totalMessagesReceived", totalMessagesReceived,
                "totalLocationUpdates", totalLocationUpdates,
                "activeBusDrivers", getActiveBusDriverCount(),
                "activeBuses", getActiveBusNumbers(),
                "realtimeLocations", lastKnownLocations.size()
        );
    }

    // 헬퍼 메서드들

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

    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long getLongValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sendSuccessMessage(WebSocketSession session, String message) {
        try {
            Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", message,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
            log.debug("✅ 성공 메시지 전송: {}", message);
        } catch (Exception e) {
            log.error("❌ 성공 메시지 전송 중 오류 발생", e);
        }
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> response = Map.of(
                    "status", "error",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);
            log.warn("⚠️ 오류 메시지 전송: {}", errorMessage);
        } catch (Exception e) {
            log.error("❌ 오류 메시지 전송 중 오류 발생", e);
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
     * 거리 계산 (미터 단위)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // 지구의 반지름 (미터)
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 하트비트 체크 - 비활성 연결 감지 및 정리
     */
    @Scheduled(fixedRate = 30000) // 30초마다 실행 (60초에서 단축)
    private void checkHeartbeats() {
        Instant threshold = Instant.now().minusSeconds(120); // 2분 임계값 (5분에서 단축)

        log.info("💓 ========== 하트비트 체크 시작 ==========");
        log.info("💓 현재 활성 세션 수: {}", lastHeartbeatMap.size());

        lastHeartbeatMap.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            Instant lastHeartbeat = entry.getValue();

            if (lastHeartbeat.isBefore(threshold)) {
                log.warn("⚠️ 하트비트 타임아웃! 세션 ID: {}", sessionId);

                // 세션 정리
                String busNumber = sessionToBusMap.get(sessionId);
                cleanupSession(sessionId, busNumber);

                return true; // 맵에서 제거
            }

            return false;
        });

        log.info("💓 하트비트 체크 완료");
        log.info("💓 ======================================");
    }

    /**
     * 유효하지 않은 세션들 정리
     */
    private void cleanupInvalidSessions() {
        driverSessions.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) {
                String busNumber = entry.getKey();
                log.info("🧹 유효하지 않은 세션 정리: 버스 번호 = {}", busNumber);

                // 관련 맵에서도 제거
                sessionToBusMap.values().removeIf(bn -> bn.equals(busNumber));
                sessionToOrganizationMap.keySet().removeIf(sid ->
                        busNumber.equals(sessionToBusMap.get(sid)));
                lastHeartbeatMap.keySet().removeIf(sid ->
                        busNumber.equals(sessionToBusMap.get(sid)));
                lastKnownLocations.remove(busNumber);
                lastLocationUpdateTime.remove(busNumber);

                return true;
            }
            return false;
        });
    }

    /**
     * 오래된 세션들 정리 (가비지 컬렉션)
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    private void cleanupStaleSessions() {
        int beforeSize = driverSessions.size();
        cleanupInvalidSessions();
        int afterSize = driverSessions.size();

        log.info("📊 ========== WebSocket 통계 (5분 주기) ==========");
        log.info("📊 총 수신 메시지: {}", totalMessagesReceived);
        log.info("📊 총 위치 업데이트: {}", totalLocationUpdates);
        log.info("📊 활성 버스 수: {} (정리됨: {})", afterSize, beforeSize - afterSize);
        log.info("📊 실시간 위치 추적 중: {}대", lastKnownLocations.size());
        log.info("📊 세션별 버스 매핑:");
        sessionToBusMap.forEach((sessionId, busNumber) -> {
            log.info("📊   - 세션 {} → 버스 {}", sessionId.substring(0, 8), busNumber);
        });
        log.info("📊 =============================================");
    }

    /**
     * 특정 세션 정리
     */
    private void cleanupSession(String sessionId, String busNumber) {
        if (busNumber != null) {
            driverSessions.remove(busNumber);
            lastKnownLocations.remove(busNumber);
            lastLocationUpdateTime.remove(busNumber);
            log.info("🧹 세션 정리: 버스 {} 제거됨", busNumber);
        }
        sessionToBusMap.remove(sessionId);
        sessionToOrganizationMap.remove(sessionId);
        lastHeartbeatMap.remove(sessionId);
    }

    /**
     * 셧다운 훅 - 애플리케이션 종료 시 리소스 정리
     */
    @PreDestroy
    public void shutdown() {
        log.warn("🛑 ========== BusDriverWebSocketHandler 종료 ==========");
        log.warn("🛑 최종 통계:");
        log.warn("🛑   - 총 메시지: {}", totalMessagesReceived);
        log.warn("🛑   - 총 위치 업데이트: {}", totalLocationUpdates);
        log.warn("🛑   - 활성 버스: {}", driverSessions.size());

        // 모든 세션 정리
        driverSessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.GOING_AWAY);
                }
            } catch (Exception e) {
                log.error("❌ 세션 종료 중 오류: {}", e.getMessage());
            }
        });

        // 스케줄러 종료
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.warn("🛑 BusDriverWebSocketHandler 종료 완료");
        log.warn("🛑 ================================================");
    }
}