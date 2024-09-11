package capston2024.bustracker.config;

import capston2024.bustracker.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@RequiredArgsConstructor
@EnableWebSecurity
@Configuration
@Slf4j
public class SecurityConfig {
    private final AuthService authService;
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("*").permitAll()
                        .anyRequest().authenticated()// 나머지는 인증 필요
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/loginForm") // 로그인 페이지 설정
                        .defaultSuccessUrl("/") // 로그인 성공 시 이동할 페이지
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(authService) // 사용자 정보 서비스를 customOAuth2UserService로 설정
                        )
                );

        return http.build();
    }
}
