package capston2024.bustracker.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 이벤트 메타 정보
 * CoShow 부스 이벤트와 같은 프로모션 이벤트 관리
 */
@Setter @Getter
@Document(collection = "events")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {
    @Id
    private String id;

    private String name; // 이벤트 이름 (예: "CoShow 2024 부스 이벤트")
    private String description; // 이벤트 설명
    private LocalDateTime startDate; // 시작 날짜
    private LocalDateTime endDate; // 종료 날짜
    private boolean isActive; // 활성화 여부
    private String organizationId; // 기관 ID
    private LocalDateTime createdAt; // 생성일
    private LocalDateTime updatedAt; // 수정일
}
