package capston2024.bustracker.config.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이벤트 참여 현황 DTO
 */
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventParticipationDTO {
    private String id;
    private String eventId;
    private String userId;
    private List<String> completedMissions; // 완료한 미션 ID 목록

    @JsonProperty("eligibleForDraw")  // JSON 직렬화 시 필드명 명시
    private boolean isEligibleForDraw; // 뽑기 자격 여부

    private boolean hasDrawn; // 뽑기 완료 여부
    private EventRewardDTO drawnReward; // 당첨된 상품
    private LocalDateTime drawTimestamp;
}
