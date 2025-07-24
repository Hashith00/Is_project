package org.example.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.example.models.User;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

public class TokenUtil {
    private static final Key SECRET_KEY = Keys.hmacShaKeyFor("supersecretkeysupersecretkey123456".getBytes());
    private static final long ACCESS_TOKEN_VALIDITY = 15 * 60 * 1000; // 15 minutes

    public static String generateAccessToken(User user) {
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    public static String generateRefreshToken() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString();
    }

    public static boolean validateAccessToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(SECRET_KEY).build().parseClaimsJws(token);
            System.out.println("Access token is vaild");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}