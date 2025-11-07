package capston2024.bustracker.config.dto;

import lombok.*;

/**
 * 미션 완료 요청 DTO
 */
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MissionCompleteRequestDTO {
    private String eventId;
    private String missionId;
    private String targetValue; // busNumber, stationId 등 검증에 필요한 값
}
