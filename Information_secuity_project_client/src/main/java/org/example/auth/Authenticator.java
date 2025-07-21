package org.example.auth;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.security.PublicKey;
import org.example.crypto.CryptoUtils;
import org.example.model.AuthToken;
import org.example.utils.DatabaseConnection;
import org.json.JSONObject;

public class Authenticator {
    public static AuthToken authenticate(BufferedReader in, PrintWriter out, BufferedReader console, PublicKey serverPublicKey) throws Exception {
        for (int i = 0; i < 2; i++) {
            String prompt = in.readLine();
            System.out.print(prompt + " ");
            String userInput = console.readLine();
            if (prompt.toLowerCase().contains("password") || prompt.toLowerCase().contains("email")) {
                userInput = CryptoUtils.encryptWithPublicKey(userInput, serverPublicKey);
            }
            out.println(userInput);
        }
        String authResult = in.readLine();
        System.out.println(authResult);
        if (authResult.trim().startsWith("{") && authResult.contains("accessToken")) {
            JSONObject obj = new JSONObject(authResult);
            String accessToken = obj.getString("accessToken");
            String refreshToken = obj.getString("refreshToken");
            AuthToken token = new AuthToken(accessToken, refreshToken);
            DatabaseConnection.saveAuthToken(token);
            return token;
        }
        if (authResult.toLowerCase().contains("failed")) {
            return null;
        }
        return null;
    }
} 