package capston2024.bustracker.domain;

import com.mongodb.DBRef;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 이벤트 미션 정보
 * 사용자가 완료해야 할 미션 정의
 */
@Setter @Getter
@Document(collection = "event_missions")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventMission {
    @Id
    private String id;

    private DBRef eventId; // 이벤트 ID
    private String title; // 미션 제목
    private String description; // 미션 설명
    private MissionType missionType; // 미션 타입
    private String targetValue; // 타겟 값 (busNumber, stationId 등)
    private boolean isRequired; // 필수 미션 여부
    private int order; // 미션 순서
    private LocalDateTime createdAt; // 생성일

    /**
     * 미션 타입 enum
     */
    public enum MissionType {
        BOARDING,           // 특정 버스 탑승
        VISIT_STATION,     // 특정 정류장 방문
        AUTO_DETECT_BOARDING // 자동 승하차 감지 완료
    }
}
