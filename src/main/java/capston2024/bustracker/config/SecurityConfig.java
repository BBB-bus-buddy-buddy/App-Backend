package capston2024.bustracker.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@RequiredArgsConstructor
@EnableWebSecurity
public class SecurityConfig {
    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/private/**").authenticated() // "/private/**"로 시작하는 URI는 인증 필요
                        .anyRequest().permitAll() // 나머지 URI는 모두 접근 허용
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/loginForm") // 로그인 페이지 설정
                        .defaultSuccessUrl("/") // 로그인 성공 시 이동할 페이지
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService) // 사용자 정보 서비스를 customOAuth2UserService로 설정
                        )
                );

        return http.build();
    }
}
