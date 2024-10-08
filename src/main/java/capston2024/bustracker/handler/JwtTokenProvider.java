package capston2024.bustracker.handler;

import capston2024.bustracker.domain.auth.TokenInfo;
import capston2024.bustracker.exception.TokenException;
import capston2024.bustracker.service.TokenService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.stream.Collectors;

import static capston2024.bustracker.exception.ErrorCode.INVALID_TOKEN;

@Component
public class JwtTokenProvider {

    public JwtTokenProvider(@Value("${JWT_SECRET}") String key, TokenService tokenService) {
        this.key = key;
        this.tokenService = tokenService;
    }
    private final String key;
    private SecretKey secretKey;
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 1000 * 60 * 30L;
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 1000 * 60 * 60L * 24 * 7;
    private static final String KEY_ROLE = "role";
    private final TokenService tokenService;

    @PostConstruct
    private void setSecretKey() {
        byte[] keyBytes = Decoders.BASE64.decode(key);
        secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, ACCESS_TOKEN_EXPIRE_TIME);
    }

    public void generateRefreshToken(Authentication authentication, String accessToken) {
        String refreshToken = generateToken(authentication, REFRESH_TOKEN_EXPIRE_TIME);
        tokenService.saveToken(authentication.getName(), refreshToken, accessToken);
    }

    private String generateToken(Authentication authentication, long expireTime) {
        Date now = new Date();
        Date expiredDate = new Date(now.getTime() + expireTime);

        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        String email = null;
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            email = (String) oAuth2User.getAttributes().get("email"); // "email" 클레임 추가
        }

        return Jwts.builder()
                .setSubject(authentication.getName())
                .claim(KEY_ROLE, authorities)
                .claim("email", email)                  // 이메일 클레임 추가
                .setIssuedAt(now)
                .setExpiration(expiredDate)
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        Collection<? extends GrantedAuthority> authorities = getAuthorities(claims);

        Map<String, Object> attributes = new HashMap<>(claims);
        OAuth2User principal = new DefaultOAuth2User(authorities, attributes, "sub");

        return new OAuth2AuthenticationToken(principal, authorities, "oauth2");
    }

    private Collection<? extends GrantedAuthority> getAuthorities(Claims claims) {
        String roles = (String) claims.get(KEY_ROLE);
        return Arrays.stream(roles.split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    public String reissueAccessToken(String accessToken) {
        if (StringUtils.hasText(accessToken)) {
            TokenInfo tokenInfo = tokenService.findByAccessToken(accessToken);
            if (tokenInfo != null && validateToken(tokenInfo.getRefreshToken())) {
                String reissueAccessToken = generateAccessToken(getAuthentication(tokenInfo.getRefreshToken()));
                tokenService.updateAccessToken(tokenInfo.getUsername(), reissueAccessToken);
                return reissueAccessToken;
            }
        }
        return null;
    }

    public boolean validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenException(INVALID_TOKEN);
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        } catch (JwtException e) {
            throw new TokenException(INVALID_TOKEN);
        }
    }
}