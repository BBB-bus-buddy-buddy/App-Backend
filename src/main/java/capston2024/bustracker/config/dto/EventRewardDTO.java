package capston2024.bustracker.config.dto;

import lombok.*;

/**
 * 이벤트 상품 DTO
 */
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventRewardDTO {
    private String id;
    private String eventId;
    private String rewardName;
    private int rewardGrade; // 1~5등
    private double probability; // 확률 (0.0 ~ 1.0)
    private int totalQuantity;
    private int remainingQuantity;
    private String imageUrl;
    private String description;
}
