package capston2024.bustracker.service;

import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ErrorCode;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class
JwtService {

    //yml 에 정의된 secret,토큰만료 시간
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refreshExpiration}")
    private Long refreshExpiration;

    // 엑세스토큰 발급
    public String generateAccessToken(User user) {
        return generateToken(user.getId(), expiration);
    }

    //리프레시 토큰 발급
    public String generateRefreshToken(User user) {
        return generateToken(user.getId(), refreshExpiration);
    }

    //토큰 생성
    private String generateToken(String userId, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    //토큰 검증
    public boolean validateAccessToken(String accessToken) {
        try {
            return isTokenTimeValid(accessToken);
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    //리프레시 토큰으로 엑세스 토큰 재발급
    public String renewAccessTokenUsingRefreshToken(String refreshToken)  {
        try {
            Claims claims = parseToken(refreshToken).getBody();
            String userId = claims.getSubject();
            return generateToken(userId, expiration);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    // payload 에 저장된 유저아이디 가지고 오기
    public Long getUserIdByParseToken(String token){
        Jws<Claims> claimsJws = parseToken(token);
        String subject = claimsJws.getBody().getSubject();
        return Long.parseLong(subject);
    }

    private Jws<Claims> parseToken(String token) throws JwtException {
        return Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token);
    }

    // 시간 검증
    private boolean isTokenTimeValid(String token) throws JwtException {
        Jws<Claims> claims = parseToken(token);
        return !claims.getBody().getExpiration().before(new Date());
    }
}