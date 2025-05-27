package capston2024.bustracker.config.dto.busEtc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 버스 좌석 정보 DTO
 * 실시간 좌석 상태와 관련 정보를 제공
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatInfoDTO {
    private String operationId;       // 운행 ID
    private String busNumber;         // 버스 번호
    private String busRealNumber;     // 실제 버스 번호
    private String routeName;         // 노선명
    private int totalSeats;           // 총 좌석 수
    private int currentPassengers;    // 현재 승객 수
    private int availableSeats;       // 사용 가능 좌석 수
    private double occupancyRate;     // 좌석 점유율 (%)
    private String driverName;        // 기사명
    private String currentStation;    // 현재 정류장
    private Double latitude;          // 현재 위도
    private Double longitude;         // 현재 경도
    private long lastUpdated;         // 마지막 업데이트 시간
    private boolean isOperating;      // 현재 운행 중 여부

    /**
     * 기본 생성자 (이전 버전 호환)
     */
    public SeatInfoDTO(String operationId, String busNumber, int totalSeats,
                       int currentPassengers, int availableSeats, long lastUpdated) {
        this.operationId = operationId;
        this.busNumber = busNumber;
        this.totalSeats = totalSeats;
        this.currentPassengers = currentPassengers;
        this.availableSeats = availableSeats;
        this.lastUpdated = lastUpdated;
        this.occupancyRate = calculateOccupancyRate();
        this.isOperating = operationId != null;
    }

    /**
     * 좌석 점유율 계산
     */
    private double calculateOccupancyRate() {
        if (totalSeats == 0) return 0.0;
        return (currentPassengers * 100.0) / totalSeats;
    }

    /**
     * 좌석 상태 텍스트 반환
     */
    public String getSeatStatusText() {
        if (availableSeats == 0) {
            return "만석";
        } else if (occupancyRate >= 80) {
            return "혼잡";
        } else if (occupancyRate >= 50) {
            return "보통";
        } else {
            return "여유";
        }
    }

    /**
     * 좌석 상태 색상 코드 반환 (UI용)
     */
    public String getSeatStatusColor() {
        if (availableSeats == 0) {
            return "#FF0000"; // 빨강
        } else if (occupancyRate >= 80) {
            return "#FFA500"; // 주황
        } else if (occupancyRate >= 50) {
            return "#FFFF00"; // 노랑
        } else {
            return "#00FF00"; // 초록
        }
    }
}