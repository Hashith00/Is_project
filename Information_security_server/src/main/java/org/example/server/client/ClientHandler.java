package org.example.server.client;
import org.example.models.User;
import org.example.server.auth.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.security.PrivateKey;
import java.util.Map;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

public class ClientHandler implements Runnable {
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
            System.err.println("Error during authentication.");
            e.printStackTrace();
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
                            System.out.println("Received expired message from client " + name + ". Dropping connection.");
                            if (out != null) {
                                sendMessage("Message too old. Connection will be closed.");
                            }
                            break; // Exit loop to close connection
                        }
                        // If not expired, process as normal
                        String message = node.get("message").asText();
                        System.out.println(name + ": " + message);
                        sendMessage(message);
                        continue;
                    }
                    if (node.has("accessToken") && node.has("message")) {
                        String accessToken = node.get("accessToken").asText();
                        String message = node.get("message").asText();
                        if (TokenUtil.validateAccessToken(accessToken)) {
                            System.out.println(name + ": " + message);
                            sendMessage(message);
                        } else {
                            if (out != null) {
                                sendMessage("Access token expired or invalid. Please renew your token using your refresh token.");
                            }
                        }
                        continue;
                    }
                } catch (Exception e) {
                    // Not a JSON message, fall through
                }
                // Fallback: print raw message and send in JSON format
                System.out.println(name + ": " + msg);
                sendMessage(msg);
            }
        } catch (IOException e) {
            System.out.println("Client " + name + " disconnected.");
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