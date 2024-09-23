package capston2024.bustracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.mongodb.internal.connection.tlschannel.util.Util.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BustrackerApplicationTests {
	@Test
	void contextLoads() {
		// 여기에 간단한 assertion 추가
	}
}