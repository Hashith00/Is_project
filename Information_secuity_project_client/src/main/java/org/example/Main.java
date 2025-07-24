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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        try {
            // Setup SSL context
            SSLContext sslContext = SSLUtils.createTrustAllSSLContext();
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            SSLSocket socket = (SSLSocket) socketFactory.createSocket(ClientConfig.SERVER_IP, ClientConfig.SERVER_PORT);

            socket.addHandshakeCompletedListener(event -> {
                logger.info("\n=== [SSL HANDSHAKE COMPLETED - CLIENT SIDE] ===");
                logger.info("Connected to  : " + event.getSession().getPeerHost());
                logger.info("Protocol      : " + event.getSession().getProtocol());
                logger.info("Cipher Suite  : " + event.getCipherSuite());
                logger.info("Session ID    : " + bytesToHex(event.getSession().getId()));
                try {
                    java.security.PublicKey serverPublicKey = org.example.ssl.SSLUtils.extractServerPublicKey((SSLSocket) event.getSocket());
                    logger.info("Server Public Key: " + serverPublicKey);
                } catch (Exception e) {
                    logger.error("Could not verify peer: " + e.getMessage());
                }
                logger.info("===============================================\n");
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
                                    logger.warn("Received message older than 1 hour. Closing connection.");
                                    try { socket.close(); } catch (Exception ignore) {}
                                    System.exit(0);
                                } else {
                                    logger.info(messageContent);
                                }
                            } else {
                                // Not a standard message, print as is
                                logger.info(msg);
                            }
                        } catch (Exception e) {
                            // Not a JSON message, print as is
                            logger.info(msg);
                        }
                    }
                } catch (Exception e) {
                    logger.info("Disconnected from server.");
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
            logger.error("Error starting client.", e);
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
