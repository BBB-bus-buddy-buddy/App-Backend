package capston2024.bustracker.domain;

import com.mongodb.DBRef;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 이벤트 참여 기록
 * 사용자별 미션 완료 및 보상 수령 현황
 */
@Setter @Getter
@Document(collection = "event_participations")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventParticipation {
    @Id
    private String id;

    private DBRef eventId; // 이벤트 ID
    private DBRef userId; // 사용자 ID

    @Builder.Default
    private List<String> completedMissions = new ArrayList<>(); // 완료한 미션 ID 목록

    private boolean isEligibleForDraw; // 뽑기 자격 여부
    private boolean hasDrawn; // 뽑기 완료 여부
    private DBRef drawnRewardId; // 당첨된 상품 ID
    private LocalDateTime drawTimestamp; // 뽑기 시각
    private LocalDateTime createdAt; // 생성일
    private LocalDateTime updatedAt; // 수정일

    /**
     * 미션 완료 추가
     */
    public void addCompletedMission(String missionId) {
        if (this.completedMissions == null) {
            this.completedMissions = new ArrayList<>();
        }
        if (!this.completedMissions.contains(missionId)) {
            this.completedMissions.add(missionId);
        }
    }

    /**
     * 뽑기 완료 처리
     */
    public void markAsDrawn(DBRef rewardId) {
        this.hasDrawn = true;
        this.drawnRewardId = rewardId;
        this.drawTimestamp = LocalDateTime.now();
    }

    public boolean hasDrawn() {
        return this.hasDrawn;
    }
}
