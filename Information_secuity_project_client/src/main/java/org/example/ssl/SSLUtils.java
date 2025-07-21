package org.example.ssl;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.PublicKey;

public class SSLUtils {
    public static SSLContext createTrustAllSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                }
        }, new SecureRandom());
        return sslContext;
    }

    public static PublicKey extractServerPublicKey(SSLSocket socket) throws Exception {
        SSLSession session = socket.getSession();
        java.security.cert.Certificate[] serverCerts = session.getPeerCertificates();
        return serverCerts[0].getPublicKey();
    }
} 