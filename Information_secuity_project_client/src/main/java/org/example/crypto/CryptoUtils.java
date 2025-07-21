package org.example.crypto;

import java.security.PublicKey;
import javax.crypto.Cipher;
import java.util.Base64;

public class CryptoUtils {
    public static String encryptWithPublicKey(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
} 