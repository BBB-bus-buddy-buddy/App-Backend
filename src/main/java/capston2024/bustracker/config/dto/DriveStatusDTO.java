package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 운행 상태 정보 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveStatusDTO {

    // 운행 일정 정보
    private String operationId;          // 운행 일정 ID
    private String status;               // 운행 상태 (SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED)

    // 버스 정보
    private String busId;                // 버스 ID
    private String busNumber;            // 버스 번호
    private String busRealNumber;        // 실제 버스 번호
    private boolean busIsOperate;        // 버스 운행 상태

    // 운전자 정보
    private String driverId;             // 운전자 ID
    private String driverName;           // 운전자 이름

    // 라우트 정보
    private String routeId;              // 라우트 ID
    private String routeName;            // 라우트 이름

    // 시간 정보
    private LocalDateTime scheduledStart; // 예정 출발 시간
    private LocalDateTime scheduledEnd;   // 예정 도착 시간
    private LocalDateTime actualStart;    // 실제 출발 시간
    private LocalDateTime actualEnd;      // 실제 도착 시간

    // 추가 정보
    private boolean isEarlyStart;        // 조기 출발 여부
    private String message;              // 상태 메시지
    private boolean hasNextDrive;        // 다음 운행 여부

    // 위치 정보 (선택)
    private LocationInfo startLocation;   // 출발지 위치
    private LocationInfo endLocation;     // 도착지 위치

    /**
     * 위치 정보 내부 클래스
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocationInfo {
        private String name;             // 장소명
        private Double latitude;         // 위도
        private Double longitude;        // 경도
    }
}