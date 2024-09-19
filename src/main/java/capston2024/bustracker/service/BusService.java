package capston2024.bustracker.service;

import capston2024.bustracker.repository.BusRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class BusService {

    private final BusRepository busRepository;
    private final KakaoApiService kakaoApiService;

}
