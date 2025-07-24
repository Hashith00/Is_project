package org.example.server.auth;

import org.example.models.User;
import org.example.utils.DatabaseConnection;
import org.example.utils.SHA256Hasher;
import org.example.utils.TokenUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.PrivateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    public static User authenticate(BufferedReader in, PrintWriter out, PrivateKey privateKey) {
        try {
            out.println("Enter email:");
            String encryptedEmail = in.readLine();
            String email = RsaDecryptor.decryptWithPrivateKey(encryptedEmail, privateKey, "email");

            out.println("Enter password:");
            String encryptedPassword = in.readLine();
            String password = RsaDecryptor.decryptWithPrivateKey(encryptedPassword, privateKey, "password");
            String hashedPassword = SHA256Hasher.hashStringSHA256(password);

            User user = DatabaseConnection.getUserByEmailAndPassword(email, hashedPassword);
            if (user == null) {
                out.println("Authentication failed. Connection will close.");
                return null;
            }
            // Generate tokens
            String accessToken = TokenUtil.generateAccessToken(user);
            String refreshToken = TokenUtil.generateRefreshToken();
            // Store refresh token in DB
            DatabaseConnection.saveRefreshToken(user.getId(), refreshToken);
            // Send tokens to client as JSON
            String json = String.format("{\"accessToken\":\"%s\",\"refreshToken\":\"%s\"}", accessToken, refreshToken);
            out.println(json);
            out.println("Welcome " + user.getName() + "! You are authenticated.");
            return user;
        } catch (IOException e) {
            logger.error("Error during authentication.", e);
            return null;
        } catch (Exception e) {
            logger.error("Error during decryption.", e);
            return null;
        }
    }
}