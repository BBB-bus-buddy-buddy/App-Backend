package capston2024.bustracker.service;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.repository.BusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class BusService {

    private final BusRepository busRepository;

    @Async("taskExecutor")
    public CompletableFuture<Bus> processBusLocationAsync(String csvData) {
        try {
            Bus bus = parseCsvToBus(csvData);
            Bus savedBus = busRepository.save(bus);
            return CompletableFuture.completedFuture(savedBus);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Bus parseCsvToBus(String csvData) {
        String[] parts = csvData.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid CSV format");
        }
        return Bus.builder()
                .location(new GeoJsonPoint(Double.parseDouble(parts[1]), Double.parseDouble(parts[0])))
                .timestamp(Instant.now())
                .build();
    }
}
