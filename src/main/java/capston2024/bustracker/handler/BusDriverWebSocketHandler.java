package capston2024.bustracker.handler;

import capston2024.bustracker.config.dto.busEtc.DriverLocationUpdateDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationDTO;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.repository.BusOperationRepository;
import capston2024.bustracker.service.BusOperationService;
import capston2024.bustracker.service.RealtimeLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class BusDriverWebSocketHandler extends TextWebSocketHandler {

    private final RealtimeLocationService realtimeLocationService;
    private final BusOperationService busOperationService;
    private final BusOperationRepository busOperationRepository;
    private final ObjectMapper objectMapper;

    // 세션 관리 (operationId -> WebSocketSession)
    private final Map<String, WebSocketSession> driverSessions = new ConcurrentHashMap<>();
    // 세션별 정보 관리
    private final Map<String, DriverSessionInfo> sessionInfoMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("기사 WebSocket 연결: {}", session.getId());

        // 연결 성공 메시지
        try {
            Map<String, Object> welcome = Map.of(
                    "type", "connection",
                    "status", "connected",
                    "message", "기사 WebSocket 연결 성공",
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
        DriverSessionInfo info = sessionInfoMap.remove(sessionId);

        if (info != null && info.operationId != null) {
            driverSessions.remove(info.operationId);

            // 운행 상태를 일시정지로 변경
            try {
                updateOperationStatus(info.operationId, BusOperation.OperationStatus.IN_PROGRESS);
            } catch (Exception e) {
                log.error("운행 상태 업데이트 실패", e);
            }

            log.info("기사 WebSocket 연결 종료: 세션={}, 운행={}, 기사={}",
                    sessionId, info.operationId, info.driverId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("기사 메시지 수신: {}", payload);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String messageType = (String) data.get("type");

            if (messageType == null) {
                sendError(session, "메시지 타입이 없습니다");
                return;
            }

            // 메시지 타입별 처리
            switch (messageType) {
                case "startOperation" -> handleStartOperation(session, data);
                case "location" -> handleLocationUpdate(session, data);
                case "endOperation" -> handleEndOperation(session, data);
                case "updatePassengers" -> handlePassengerUpdate(session, data);
                case "emergency" -> handleEmergency(session, data);
                case "ping" -> handlePing(session);
                default -> sendError(session, "알 수 없는 메시지 타입: " + messageType);
            }

        } catch (Exception e) {
            log.error("기사 메시지 처리 중 오류", e);
            sendError(session, "메시지 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 운행 시작 처리
     */
    private void handleStartOperation(WebSocketSession session, Map<String, Object> data) {
        try {
            String operationId = (String) data.get("operationId");
            String driverId = (String) data.get("driverId");
            String organizationId = (String) data.get("organizationId");

            if (operationId == null || driverId == null || organizationId == null) {
                sendError(session, "필수 정보가 누락되었습니다");
                return;
            }

            // 운행 정보 확인
            BusOperation operation = busOperationRepository.findByOperationId(operationId)
                    .orElse(null);

            if (operation == null) {
                sendError(session, "운행 정보를 찾을 수 없습니다");
                return;
            }

            // 세션 정보 저장
            DriverSessionInfo info = new DriverSessionInfo(
                    operationId, driverId, organizationId, System.currentTimeMillis()
            );
            sessionInfoMap.put(session.getId(), info);
            driverSessions.put(operationId, session);

            // 운행 상태 업데이트
            updateOperationStatus(operationId, BusOperation.OperationStatus.IN_PROGRESS);

            // 운행 정보 전송
            BusOperationDTO operationDTO = busOperationService.convertToDTO(operation);
            Map<String, Object> response = Map.of(
                    "type", "operationStarted",
                    "operation", operationDTO,
                    "message", "운행이 시작되었습니다",
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);

            log.info("운행 시작: 운행={}, 기사={}", operationId, driverId);

        } catch (Exception e) {
            log.error("운행 시작 처리 중 오류", e);
            sendError(session, "운행 시작 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 위치 업데이트 처리
     */
    private void handleLocationUpdate(WebSocketSession session, Map<String, Object> data) {
        try {
            DriverSessionInfo info = sessionInfoMap.get(session.getId());
            if (info == null || info.operationId == null) {
                sendError(session, "운행 정보가 없습니다. 먼저 운행을 시작하세요.");
                return;
            }

            Map<String, Object> locationData = (Map<String, Object>) data.get("data");
            if (locationData == null) {
                sendError(session, "위치 데이터가 없습니다");
                return;
            }

            // DriverLocationUpdateDTO 생성
            DriverLocationUpdateDTO locationUpdate = new DriverLocationUpdateDTO();
            locationUpdate.setOperationId(info.operationId);
            locationUpdate.setLatitude(((Number) locationData.get("latitude")).doubleValue());
            locationUpdate.setLongitude(((Number) locationData.get("longitude")).doubleValue());
            locationUpdate.setCurrentPassengers(
                    locationData.containsKey("currentPassengers") ?
                            ((Number) locationData.get("currentPassengers")).intValue() : 0
            );
            locationUpdate.setTimestamp(System.currentTimeMillis());

            // 실시간 위치 업데이트 처리
            boolean success = realtimeLocationService.updateDriverLocation(locationUpdate);

            // 세션 활성 시간 업데이트
            info.lastActivity = System.currentTimeMillis();

            // 응답 전송
            Map<String, Object> response = Map.of(
                    "type", "locationAck",
                    "status", success ? "success" : "failed",
                    "message", success ? "위치 업데이트 완료" : "위치 업데이트 실패",
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);

            // 주변 승객 정보 전송 (선택적)
            if (success && locationData.containsKey("requestNearbyPassengers") &&
                    (Boolean) locationData.get("requestNearbyPassengers")) {
                sendNearbyPassengersInfo(session, info.operationId,
                        locationUpdate.getLatitude(), locationUpdate.getLongitude());
            }

        } catch (Exception e) {
            log.error("위치 업데이트 처리 중 오류", e);
            sendError(session, "위치 업데이트 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 운행 종료 처리
     */
    private void handleEndOperation(WebSocketSession session, Map<String, Object> data) {
        try {
            DriverSessionInfo info = sessionInfoMap.get(session.getId());
            if (info == null || info.operationId == null) {
                sendError(session, "운행 정보가 없습니다");
                return;
            }

            // 운행 상태 업데이트
            updateOperationStatus(info.operationId, BusOperation.OperationStatus.COMPLETED);

            // 실시간 위치 정보 제거
            realtimeLocationService.removeOperationLocation(info.operationId);

            // 세션 정리
            driverSessions.remove(info.operationId);
            sessionInfoMap.remove(session.getId());

            Map<String, Object> response = Map.of(
                    "type", "operationEnded",
                    "message", "운행이 종료되었습니다",
                    "operationId", info.operationId,
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);

            log.info("운행 종료: 운행={}, 기사={}", info.operationId, info.driverId);

        } catch (Exception e) {
            log.error("운행 종료 처리 중 오류", e);
            sendError(session, "운행 종료 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 승객 수 업데이트 처리
     */
    private void handlePassengerUpdate(WebSocketSession session, Map<String, Object> data) {
        try {
            DriverSessionInfo info = sessionInfoMap.get(session.getId());
            if (info == null || info.operationId == null) {
                sendError(session, "운행 정보가 없습니다");
                return;
            }

            Map<String, Object> passengerData = (Map<String, Object>) data.get("data");
            int currentPassengers = ((Number) passengerData.get("currentPassengers")).intValue();

            // BusOperation 승객 수 업데이트
            busOperationRepository.findByOperationId(info.operationId).ifPresent(operation -> {
                operation.setTotalPassengers(currentPassengers);
                busOperationRepository.save(operation);
            });

            Map<String, Object> response = Map.of(
                    "type", "passengerUpdateAck",
                    "currentPassengers", currentPassengers,
                    "message", "승객 수가 업데이트되었습니다",
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);

        } catch (Exception e) {
            log.error("승객 수 업데이트 처리 중 오류", e);
            sendError(session, "승객 수 업데이트 중 오류: " + e.getMessage());
        }
    }

    /**
     * 긴급 상황 처리
     */
    private void handleEmergency(WebSocketSession session, Map<String, Object> data) {
        try {
            DriverSessionInfo info = sessionInfoMap.get(session.getId());
            if (info == null) {
                sendError(session, "운행 정보가 없습니다");
                return;
            }

            String emergencyType = (String) data.get("emergencyType");
            String description = (String) data.get("description");
            Map<String, Object> location = (Map<String, Object>) data.get("location");

            log.error("긴급 상황 발생: 운행={}, 유형={}, 설명={}",
                    info.operationId, emergencyType, description);

            // 긴급 상황 이벤트 저장
            Map<String, Object> metadata = Map.of(
                    "emergencyType", emergencyType,
                    "description", description,
                    "driverId", info.driverId
            );

            // TODO: 긴급 상황 알림 시스템 연동

            Map<String, Object> response = Map.of(
                    "type", "emergencyAck",
                    "message", "긴급 상황이 접수되었습니다",
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, response);

        } catch (Exception e) {
            log.error("긴급 상황 처리 중 오류", e);
        }
    }

    /**
     * 핑 처리
     */
    private void handlePing(WebSocketSession session) {
        try {
            DriverSessionInfo info = sessionInfoMap.get(session.getId());
            if (info != null) {
                info.lastActivity = System.currentTimeMillis();
            }

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
     * 주변 승객 정보 전송
     */
    private void sendNearbyPassengersInfo(WebSocketSession session, String operationId,
                                          double latitude, double longitude) {
        try {
            // TODO: 주변 승객 위치 정보 조회 로직 구현
            // 현재는 더미 데이터 전송
            Map<String, Object> nearbyInfo = Map.of(
                    "type", "nearbyPassengers",
                    "operationId", operationId,
                    "totalNearby", 0,
                    "passengers", List.of(),
                    "timestamp", System.currentTimeMillis()
            );
            sendMessage(session, nearbyInfo);
        } catch (Exception e) {
            log.error("주변 승객 정보 전송 실패", e);
        }
    }

    /**
     * 운행 상태 업데이트
     */
    private void updateOperationStatus(String operationId, BusOperation.OperationStatus status) {
        busOperationRepository.findByOperationId(operationId).ifPresent(operation -> {
            operation.setStatus(status);
            if (status == BusOperation.OperationStatus.IN_PROGRESS && operation.getActualStart() == null) {
                operation.setActualStart(LocalDateTime.now());
            } else if (status == BusOperation.OperationStatus.COMPLETED && operation.getActualEnd() == null) {
                operation.setActualEnd(LocalDateTime.now());
            }
            busOperationRepository.save(operation);
        });
    }

    /**
     * 특정 운행의 기사에게 메시지 전송 (서버에서 기사에게)
     */
    public void sendMessageToDriver(String operationId, Object message) {
        WebSocketSession session = driverSessions.get(operationId);
        if (session != null && session.isOpen()) {
            try {
                sendMessage(session, message);
            } catch (Exception e) {
                log.error("기사({})에게 메시지 전송 실패", operationId, e);
            }
        }
    }

    /**
     * 연결 상태 확인 (30초마다)
     */
    @Scheduled(fixedDelay = 30000)
    public void checkConnections() {
        long currentTime = System.currentTimeMillis();
        long timeout = 90000; // 90초

        sessionInfoMap.entrySet().removeIf(entry -> {
            DriverSessionInfo info = entry.getValue();
            boolean expired = (currentTime - info.lastActivity) > timeout;

            if (expired) {
                log.warn("비활성 기사 세션 제거: 세션={}, 운행={}",
                        entry.getKey(), info.operationId);
                if (info.operationId != null) {
                    driverSessions.remove(info.operationId);
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
     * 기사 세션 정보
     */
    private static class DriverSessionInfo {
        String operationId;
        String driverId;
        String organizationId;
        long lastActivity;

        DriverSessionInfo(String operationId, String driverId, String organizationId, long lastActivity) {
            this.operationId = operationId;
            this.driverId = driverId;
            this.organizationId = organizationId;
            this.lastActivity = lastActivity;
        }
    }
}