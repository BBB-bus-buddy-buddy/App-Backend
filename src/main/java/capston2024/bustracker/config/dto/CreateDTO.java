package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateDTO {
    private String SchoolName;
    private double latitude;  // 위도
    private double longitude; // 경도
}
