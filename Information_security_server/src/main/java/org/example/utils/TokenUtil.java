package org.example.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.example.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

public class TokenUtil {
    private static final Logger logger = LoggerFactory.getLogger(TokenUtil.class);
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
            logger.info("\nAccess token is vaild");
            logger.info("Access token validation successful");
            return true;
        } catch (Exception e) {
            logger.warn("Access token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public static boolean validateRefreshToken(String token) {
        logger.debug("Validating refresh token");
        // Get user ID from refresh token in database
        User user = DatabaseConnection.getUserByRefreshToken(token);
        if (user == null) {
            logger.warn("Refresh token validation failed: No user found for token");
            return false;
        }
        // Check if refresh token is valid for this user
        boolean isValid = DatabaseConnection.isRefreshTokenValid(user.getId(), token);
        if (isValid) {
            logger.info("Refresh token validation successful for user: {}", user.getName());
        } else {
            logger.warn("Refresh token validation failed for user: {}", user.getName());
        }
        return isValid;
    }

    public static String generateNewAccessTokenFromRefreshToken(String refreshToken) {
        logger.info("Attempting to generate new access token from refresh token");
        // Get user from refresh token
        User user = DatabaseConnection.getUserByRefreshToken(refreshToken);
        if (user == null) {
            logger.error("Failed to generate new access token: No user found for refresh token");
            return null;
        }
        
        // Validate refresh token
        if (!DatabaseConnection.isRefreshTokenValid(user.getId(), refreshToken)) {
            logger.error("Failed to generate new access token: Refresh token is invalid for user {}", user.getName());
            return null;
        }
        
        // Generate new access token
        logger.info("Successfully generated new access token for user: {}", user.getName());
        return generateAccessToken(user);
    }
}