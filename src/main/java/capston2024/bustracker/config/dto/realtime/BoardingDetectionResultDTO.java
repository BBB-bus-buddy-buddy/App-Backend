package capston2024.bustracker.config.dto.realtime;

import capston2024.bustracker.config.dto.busEtc.BusBoardingDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 승객 탑승/하차 감지 결과
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardingDetectionResultDTO {
    private String userId;                // 사용자 ID
    private String operationId;           // 운행 ID
    private String busNumber;             // 버스 번호
    private BusBoardingDTO.BoardingAction action; // BOARD/ALIGHT
    private boolean autoDetected;         // 자동 감지 여부
    private double detectionDistance;     // 감지 거리
    private long timestamp;               // 감지 시간
    private boolean successful;           // 처리 성공 여부
    private String message;               // 결과 메시지
}