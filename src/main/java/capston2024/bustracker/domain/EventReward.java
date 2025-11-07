package capston2024.bustracker.domain;

import com.mongodb.DBRef;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 이벤트 상품 정보
 * 등급별 상품 및 확률 정의
 */
@Setter @Getter
@Document(collection = "event_rewards")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventReward {
    @Id
    private String id;

    private DBRef eventId; // 이벤트 ID
    private String rewardName; // 상품 이름
    private int rewardGrade; // 등급 (1~5등)
    private double probability; // 당첨 확률 (0.0 ~ 1.0)
    private int totalQuantity; // 전체 수량
    private int remainingQuantity; // 남은 수량
    private String imageUrl; // 상품 이미지 URL
    private String description; // 상품 설명
    private LocalDateTime createdAt; // 생성일
    private LocalDateTime updatedAt; // 수정일
}
