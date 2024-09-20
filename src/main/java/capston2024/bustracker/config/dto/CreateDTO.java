package capston2024.bustracker.config.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateDTO {
    @NotNull
    private String SchoolName;
    private double latitude;  // 위도
    private double longitude; // 경도
}
