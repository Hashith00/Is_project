package org.example.server;

import org.example.server.client.ClientHandler;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        try {
            // Load the keystore
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try (FileInputStream keyStoreStream = new FileInputStream(ServerConfig.KEYSTORE_FILE)) {
                keyStore.load(keyStoreStream, ServerConfig.KEYSTORE_PASSWORD.toCharArray());
            }

            // Initialize key manager
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keyStore, ServerConfig.KEYSTORE_PASSWORD.toCharArray());

            // Load private key for decryption
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(ServerConfig.KEY_ALIAS, ServerConfig.KEYSTORE_PASSWORD.toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(ServerConfig.PORT);

            logger.info("TLS Chat server started on port " + ServerConfig.PORT);

            Map<String, ClientHandler> clients = new HashMap<>();

            new Thread(() -> {
                //noinspection resource
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    logger.info("\nConnected clients: " + clients.keySet());
                    logger.info("Enter client name to chat with: ");
                    String targetClient = scanner.nextLine().trim();

                    ClientHandler target = clients.get(targetClient);
                    if (target == null) {
                        logger.info("Client not found.");
                        continue;
                    }

                    logger.info("Chatting with " + targetClient + " (type 'exit' to stop):");
                    while (true) {
                        logger.info("You: ");
                        String msg = scanner.nextLine();
                        if (msg.equalsIgnoreCase("exit")) break;
                        target.sendMessage("Server: " + msg);
                    }
                }
            }).start();

            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                clientSocket.addHandshakeCompletedListener(event -> {
                    logger.info("\n=== [SSL HANDSHAKE COMPLETED - SERVER SIDE] ===");
                    logger.info("Peer Host     : " + event.getSession().getPeerHost());
                    logger.info("Protocol      : " + event.getSession().getProtocol());
                    logger.info("Cipher Suite  : " + event.getCipherSuite());
                    logger.info("Session ID    : " + bytesToHex(event.getSession().getId()));
                    logger.info("==============================================\n");
                });

                clientSocket.startHandshake();

                new Thread(() -> {
                    try {
                        ClientHandler handler = new ClientHandler(clientSocket, privateKey, clients);
                        if (handler.authenticate()) {
                            clients.put(handler.getName(), handler);
                            logger.info(handler.getName() + " connected via TLS.");
                            new Thread(handler).start();
                        } else {
                            logger.info("Authentication failed for client.");
                        }
                    } catch (Exception e) {
                        logger.error("Error handling client connection.", e);
                    }
                }).start();
            }

        } catch (Exception e) {
            logger.error("Error starting server.", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
} 