package capston2024.bustracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("classpasth:env.properties")
public class BustrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BustrackerApplication.class, args);
	}

}
