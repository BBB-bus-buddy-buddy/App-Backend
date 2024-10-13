package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class ArrivalTimeResponseDTO {
    private String name;
    private int duration;  // 도착까지의 총 소요 시간 (초 단위)

    // 시, 분, 초 단위로 변환하여 문자열로 반환하는 메소드
    public String getFormattedDuration() {
        int hours = duration / 3600;
        int minutes = (duration % 3600) / 60;
        int seconds = duration % 60;
        StringBuilder formattedTime = new StringBuilder();
        if (hours > 0) formattedTime.append(hours).append("시간 ");
        if (minutes > 0) formattedTime.append(minutes).append("분 ");
        if (seconds > 0 || formattedTime.length() == 0) formattedTime.append(seconds).append("초"); // 0초일 때도 출력해야 하는 경우
        return formattedTime.toString().trim();
    }

}
