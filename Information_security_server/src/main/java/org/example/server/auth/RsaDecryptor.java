package org.example.server.auth;

import javax.crypto.Cipher;
import java.security.PrivateKey;
import java.util.Base64;

public class RsaDecryptor {
    public static String decryptWithPrivateKey(String base64Encrypted, PrivateKey privateKey, String label) throws Exception {
        System.out.println("[Decryption] Received encrypted " + label + ": " + base64Encrypted);
        byte[] encryptedBytes = Base64.getDecoder().decode(base64Encrypted);
        System.out.println("[Decryption] Base64-decoded bytes for " + label + ": " + bytesToHex(encryptedBytes));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        String decrypted = new String(decryptedBytes, "UTF-8");
        System.out.println("[Decryption] Decrypted " + label + ": " + decrypted);
        return decrypted;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}