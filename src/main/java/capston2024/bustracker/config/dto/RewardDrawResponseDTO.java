package capston2024.bustracker.config.dto;

import lombok.*;

/**
 * 랜덤 뽑기 결과 DTO
 */
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RewardDrawResponseDTO {
    private boolean success;
    private EventRewardDTO reward; // 당첨된 상품
    private String message;
}
