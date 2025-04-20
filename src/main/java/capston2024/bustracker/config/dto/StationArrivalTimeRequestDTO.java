package capston2024.bustracker.config.dto;

import com.mongodb.lang.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
@AllArgsConstructor
public class StationArrivalTimeRequestDTO {
    @Nullable
    private String name;
    private Double x;
    private Double y;
}