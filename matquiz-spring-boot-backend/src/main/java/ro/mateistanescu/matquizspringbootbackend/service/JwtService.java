package ro.mateistanescu.matquizspringbootbackend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.accessTokenValidityMS}")
    private int jwtExpirationMs;

    public String createToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());

        return Jwts.builder()
                .header()
                .and()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    public String validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());

        JwtParser parser = Jwts.parser()
                .verifyWith(key)
                .build();

        try {
            String normalizedToken = token.startsWith("Bearer ")
                    ? token.substring(7).trim()
                    : token.trim();

            Jws<Claims> jwt = parser.parseSignedClaims(normalizedToken);
            return jwt.getPayload().getSubject();
        } catch (Exception ex) {
            throw new UsernameNotFoundException("Invalid token");
        }
    }
}
