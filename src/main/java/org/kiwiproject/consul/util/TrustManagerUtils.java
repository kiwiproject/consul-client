package org.kiwiproject.consul.util;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

public class TrustManagerUtils {

    private TrustManagerUtils() {
        // utility class
    }

    public static X509TrustManager getDefaultTrustManager() {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager trustManager) {
                    return trustManager;
                }
            }
            throw new IllegalStateException("Default X509TrustManager not found");
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new UncheckedGeneralSecurityException(e);
        }
    }

}
