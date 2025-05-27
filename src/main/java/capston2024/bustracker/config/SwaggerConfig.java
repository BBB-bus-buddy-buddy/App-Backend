package capston2024.bustracker.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Bus Tracker API",
                version = "v1.0",
                description = "버스 실시간 위치 추적 및 좌석 정보 제공 시스템 API",
                contact = @Contact(
                        name = "Bus Tracker Team",
                        email = "support@bustracker.com"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "개발 서버"),
                @Server(url = "https://api.bustracker.com", description = "운영 서버")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER,
        description = "JWT 토큰을 입력하세요"
)
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .tags(List.of(
                        new Tag().name("인증 관리").description("사용자 인증 및 권한 관리 API"),
                        new Tag().name("사용자 관리").description("일반 사용자 정보 관리 API"),
                        new Tag().name("조직 관리").description("조직(기관) 관리 API"),
                        new Tag().name("버스 관리").description("버스 정보 관리 API"),
                        new Tag().name("버스 운행 관리").description("버스 운행 스케줄 및 상태 관리 API"),
                        new Tag().name("노선 관리").description("버스 노선 정보 관리 API"),
                        new Tag().name("정류장 관리").description("버스 정류장 정보 관리 API"),
                        new Tag().name("좌석 정보").description("실시간 버스 좌석 정보 조회 API"),
                        new Tag().name("운전자 관리").description("버스 운전자 관련 API"),
                        new Tag().name("직원 관리").description("조직 직원 인증 API"),
                        new Tag().name("관리자").description("시스템 총관리자 전용 API"),
                        new Tag().name("외부 API 연동").description("카카오 등 외부 API 연동")
                ));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public-apis")
                .displayName("공개 API")
                .pathsToMatch("/api/auth/**", "/api/driver/verify", "/api/organization/verify")
                .build();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("user-apis")
                .displayName("사용자 API")
                .pathsToMatch("/api/user/**", "/api/station/**", "/api/routes/**", "/api/seats/**")
                .build();
    }

    @Bean
    public GroupedOpenApi staffApi() {
        return GroupedOpenApi.builder()
                .group("staff-apis")
                .displayName("조직 관리자 API")
                .pathsToMatch("/api/bus/**", "/api/bus-operations/**", "/api/staff/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin-apis")
                .displayName("총관리자 API")
                .pathsToMatch("/admin/**", "/api/organization/**")
                .build();
    }

    @Bean
    public GroupedOpenApi externalApi() {
        return GroupedOpenApi.builder()
                .group("external-apis")
                .displayName("외부 연동 API")
                .pathsToMatch("/api/kakao-api/**")
                .build();
    }
}