package org.example.server;

import org.example.server.client.ClientHandler;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {
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

            System.out.println("TLS Chat server started on port " + ServerConfig.PORT);

            Map<String, ClientHandler> clients = new HashMap<>();

            new Thread(() -> {
                //noinspection resource
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    System.out.println("\nConnected clients: " + clients.keySet());
                    System.out.print("Enter client name to chat with: ");
                    String targetClient = scanner.nextLine().trim();

                    ClientHandler target = clients.get(targetClient);
                    if (target == null) {
                        System.out.println("Client not found.");
                        continue;
                    }

                    System.out.println("Chatting with " + targetClient + " (type 'exit' to stop):");
                    while (true) {
                        System.out.print("You: ");
                        String msg = scanner.nextLine();
                        if (msg.equalsIgnoreCase("exit")) break;
                        target.sendMessage("Server: " + msg);
                    }
                }
            }).start();

            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                clientSocket.addHandshakeCompletedListener(event -> {
                    System.out.println("\n=== [SSL HANDSHAKE COMPLETED - SERVER SIDE] ===");
                    System.out.println("Peer Host     : " + event.getSession().getPeerHost());
                    System.out.println("Protocol      : " + event.getSession().getProtocol());
                    System.out.println("Cipher Suite  : " + event.getCipherSuite());
                    System.out.println("Session ID    : " + bytesToHex(event.getSession().getId()));
                    System.out.println("==============================================\n");
                });

                clientSocket.startHandshake();

                new Thread(() -> {
                    try {
                        ClientHandler handler = new ClientHandler(clientSocket, privateKey, clients);
                        if (handler.authenticate()) {
                            clients.put(handler.getName(), handler);
                            System.out.println(handler.getName() + " connected via TLS.");
                            new Thread(handler).start();
                        } else {
                            System.out.println("Authentication failed for client.");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
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
} 