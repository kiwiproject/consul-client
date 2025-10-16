package org.kiwiproject.consul.util;

import com.google.common.annotations.VisibleForTesting;

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
        return getTrustManager(TrustManagerFactory.getDefaultAlgorithm());
    }

    @VisibleForTesting
    static X509TrustManager getTrustManager(String algorithm) {
        try {
            return getTrustManager(TrustManagerFactory.getInstance(algorithm));
        } catch (NoSuchAlgorithmException e) {
            throw new UncheckedGeneralSecurityException(e);
        }
    }

    @VisibleForTesting
    static X509TrustManager getTrustManager(TrustManagerFactory factory) {
        try {
            factory.init((KeyStore) null);
            for (TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager trustManager) {
                    return trustManager;
                }
            }
            throw new IllegalStateException("Default X509TrustManager not found");
        } catch (KeyStoreException e) {
            throw new UncheckedGeneralSecurityException(e);
        }
    }
}
