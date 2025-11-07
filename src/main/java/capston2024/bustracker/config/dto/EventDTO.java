package capston2024.bustracker.config.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이벤트 정보 DTO
 */
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventDTO {
    private String id;
    private String name;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean isActive;
    private String organizationId;
    private List<EventMissionDTO> missions; // 미션 목록
    private List<EventRewardDTO> rewards; // 상품 목록
    private LocalDateTime createdAt;
}
