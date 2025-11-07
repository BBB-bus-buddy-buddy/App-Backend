package capston2024.bustracker.config.dto;

import capston2024.bustracker.domain.EventMission.MissionType;
import lombok.*;

/**
 * 이벤트 미션 DTO
 */
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventMissionDTO {
    private String id;
    private String eventId;
    private String title;
    private String description;
    private MissionType missionType;
    private String targetValue;
    private boolean isRequired;
    private int order;
    private boolean isCompleted; // 사용자의 완료 여부 (클라이언트용)
}
