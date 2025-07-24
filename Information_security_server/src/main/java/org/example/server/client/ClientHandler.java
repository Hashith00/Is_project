package org.example.server.client;
import org.example.models.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.server.auth.AuthService;
import org.example.utils.TokenUtil;

import java.io.*;
import java.net.Socket;
import java.security.PrivateKey;
import java.util.Map;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Socket socket;
    private final PrivateKey privateKey;
    private final Map<String, ClientHandler> clients;
    private String name;
    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket, PrivateKey privateKey, Map<String, ClientHandler> clients) {
        this.socket = socket;
        this.privateKey = privateKey;
        this.clients = clients;
    }

    public String getName() {
        return name;
    }

    public boolean authenticate() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            User user = AuthService.authenticate(in, out, privateKey);
            if (user == null) {
                socket.close();
                return false;
            }
            this.name = user.getName();
            return true;
        } catch (IOException e) {
            logger.error("Error during authentication.", e);
            return false;
        }
    }

    public void sendMessage(String msg) {
        if (out != null) {
            String json = String.format("{\"time_stamp\":\"%s\",\"message\":\"%s\"}",
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()), msg.replace("\"", "\\\""));
            out.println(json);
        }
    }
    public void sendRefreshToken(String msg, String refreshToken) {
        if (out != null) {
            // Generate new access token from refresh token
            String newAccessToken = TokenUtil.generateNewAccessTokenFromRefreshToken(refreshToken);
            if (newAccessToken != null) {
                String json = String.format("{\"time_stamp\":\"%s\",\"message\":\"%s\",\"accessToken\":\"%s\"}",
                    DateTimeFormatter.ISO_INSTANT.format(Instant.now()), 
                    msg.replace("\"", "\\\""), 
                    newAccessToken.replace("\"", "\\\""));
                out.println(json);
            } else {
                String json = String.format("{\"time_stamp\":\"%s\",\"message\":\"%s\"}",
                    DateTimeFormatter.ISO_INSTANT.format(Instant.now()), 
                    "Failed to generate new access token. Please login again.".replace("\"", "\\\""));
                out.println(json);
            }
        }
    }

    @Override
    public void run() {
        try {
            String msg;
            ObjectMapper mapper = new ObjectMapper();
            while ((msg = in.readLine()) != null) {
                // Try to parse as JSON with accessToken
                try {
                    JsonNode node = mapper.readTree(msg);
                    // Check for time_stamp and message
                    if (node.has("time_stamp") && node.has("message")) {
                        String timeStampStr = node.get("time_stamp").asText();
                        Instant msgTime = Instant.parse(timeStampStr);
                        Instant now = Instant.now();
                        if (Duration.between(msgTime, now).toHours() >= 1) {
                            logger.warn("Received expired message from client {}. Dropping connection.", name);
                            if (out != null) {
                                sendMessage("Message too old. Connection will be closed.");
                            }
                            break; // Exit loop to close connection
                        }
                        // If not expired, process as normal
                        String message = node.get("message").asText();
                        logger.info("{}: {}", name, message);
                        sendMessage(message);
                        continue;
                    }
                    if (node.has("accessToken") && node.has("message")) {
                        String accessToken = node.get("accessToken").asText();
                        String message = node.get("message").asText();
                        if (TokenUtil.validateAccessToken(accessToken)) {
                            logger.info("{}: {}", name, message);
                            sendMessage(message);
                        } else {
                            if (out != null) {
                                sendMessage("Access token expired or invalid. Please renew your token using your refresh token.");
                            }
                        }
                        continue;
                    }
                    if (node.has("refreshToken")) {
                        String refreshToken = node.get("refreshToken").asText();
                        if (TokenUtil.validateRefreshToken(refreshToken)) {
                            logger.info("{}: Refresh token is valid", name);
                            sendRefreshToken("Refresh token is valid. Renewing access token...", refreshToken);
                        } else {
                            logger.warn("{}: Refresh token is invalid", name);
                            // sendRefreshToken("Refresh token is invalid. Please login again.", refreshToken);
                        }
                    }
                } catch (Exception e) {
                    // Not a JSON message, fall through
                }
                // Fallback: print raw message and send in JSON format
                Instant msgTime = Instant.now();
                logger.info("{}: {}", name, msg);
                sendMessage(msg);
            }
        } catch (IOException e) {
            logger.info("Client {} disconnected.", name);
        } finally {
            try {
                clients.remove(name);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
} 