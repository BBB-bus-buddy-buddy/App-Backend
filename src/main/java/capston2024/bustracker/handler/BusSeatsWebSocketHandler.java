package capston2024.bustracker.handler;

import capston2024.bustracker.repository.BusRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusSeatsWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final BusRepository busRepository;

    
}
