package org.example;
import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.Cipher;
import org.example.ssl.SSLUtils;
import org.example.auth.Authenticator;
import org.example.model.AuthToken;
import org.json.JSONObject;
import java.time.Instant;
import java.time.Duration;


public class Main {
    public static void main(String[] args) {
        try {
            // Setup SSL context
            SSLContext sslContext = SSLUtils.createTrustAllSSLContext();
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            SSLSocket socket = (SSLSocket) socketFactory.createSocket(ClientConfig.SERVER_IP, ClientConfig.SERVER_PORT);

            socket.addHandshakeCompletedListener(event -> {
                System.out.println("\n=== [SSL HANDSHAKE COMPLETED - CLIENT SIDE] ===");
                System.out.println("Connected to  : " + event.getSession().getPeerHost());
                System.out.println("Protocol      : " + event.getSession().getProtocol());
                System.out.println("Cipher Suite  : " + event.getCipherSuite());
                System.out.println("Session ID    : " + bytesToHex(event.getSession().getId()));
                try {
                    java.security.PublicKey serverPublicKey = org.example.ssl.SSLUtils.extractServerPublicKey((SSLSocket) event.getSocket());
                    System.out.println("Server Public Key: " + serverPublicKey);
                } catch (Exception e) {
                    System.out.println("Could not verify peer: " + e.getMessage());
                }
                System.out.println("===============================================\n");
            });

            socket.startHandshake();

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            // Extract server public key
            java.security.PublicKey serverPublicKey = SSLUtils.extractServerPublicKey(socket);

            // Authentication flow
            final AuthToken[] token = {Authenticator.authenticate(in, out, console, serverPublicKey)};
            if (token[0] == null) {
                socket.close();
                return;
            }

            // Start reading messages from server
            final BufferedReader finalIn = in;
            final PrintWriter finalOut = out;
            final Object tokenLock = new Object();
            Thread messageThread = new Thread(() -> {
                try {
                    String msg;
                    while ((msg = finalIn.readLine()) != null) {
                        try {
                            JSONObject msgObj = new JSONObject(msg);
                            if (msgObj.has("time_stamp") && msgObj.has("message")) {
                                String timeStampStr = msgObj.getString("time_stamp");
                                String messageContent = msgObj.getString("message");
                                Instant messageTime = Instant.parse(timeStampStr);
                                Instant now = Instant.now();
                                Duration duration = Duration.between(messageTime, now);
                                if (duration.toHours() >= 1) {
                                    System.out.println("Received message older than 1 hour. Closing connection.");
                                    try { socket.close(); } catch (Exception ignore) {}
                                    System.exit(0);
                                } else {
                                    System.out.println(messageContent);
                                }
                            } else {
                                // Not a standard message, print as is
                                System.out.println(msg);
                            }
                        } catch (Exception e) {
                            // Not a JSON message, print as is
                            System.out.println(msg);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Disconnected from server.");
                }
            });
            messageThread.start();

            // Sending messages
            String input;
            while ((input = console.readLine()) != null) {
                JSONObject msgObj = new JSONObject();
                synchronized (tokenLock) {
                    msgObj.put("accessToken", token[0].getAccessToken());
                }
                msgObj.put("message", input);
                out.println(msgObj.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static String encryptWithPublicKey(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}
