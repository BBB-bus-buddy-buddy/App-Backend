package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusDTO {
    private String id;              // MongoDB에서 생성되는 id
    private String busNumber;       // 버스 번호 (3~6자리 고유 코드)
    private String organizationId;  // 조직 ID (필수)
    private String routeId;         // 라우트 ID
    private int totalSeats;         // 전체 좌석 수
    private int occupiedSeats;      // 사용 중인 좌석 수
    private int availableSeats;     // 사용 가능한 좌석 수
}