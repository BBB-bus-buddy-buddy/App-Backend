package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StationPeakInfoDTO {
    private String label;
    private long boardings;
    private long alightings;
}
