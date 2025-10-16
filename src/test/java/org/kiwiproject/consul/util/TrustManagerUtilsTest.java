package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

class TrustManagerUtilsTest {
    @Test
    void shouldTrustManagerReturnCorrectResult() {
        assertThat(TrustManagerUtils.getDefaultTrustManager()).isNotNull();
    }

    @Test
    void shouldThrowWhenGivenInvalidAlgorithm() {
        assertThatThrownBy(() -> TrustManagerUtils.getTrustManager("foo"))
                .isExactlyInstanceOf(UncheckedGeneralSecurityException.class)
                .cause()
                .isInstanceOf(NoSuchAlgorithmException.class);
    }

    @Test
    void shouldThrowWhenInitFails() throws KeyStoreException {
        var factory = mock(TrustManagerFactory.class);
        doThrow(new KeyStoreException()).when(factory).init(isNull(KeyStore.class));

        assertThatThrownBy(() -> TrustManagerUtils.getTrustManager(factory))
                .isExactlyInstanceOf(UncheckedGeneralSecurityException.class)
                .cause()
                .isInstanceOf(KeyStoreException.class);
    }

    @Test
    void shouldThrowWhenThereAreNoTrustManagers() {
        var factory = mock(TrustManagerFactory.class);
        when(factory.getTrustManagers()).thenReturn(new TrustManager[0]);

        assertThatIllegalStateException()
                .isThrownBy(() -> TrustManagerUtils.getTrustManager(factory))
                .withMessage("Default X509TrustManager not found");
    }

    @Test
    void shouldThrowWhenThereIsNoX509TrustManager() {
        var factory = mock(TrustManagerFactory.class);
        class MyTrustManager implements TrustManager {
        }
        when(factory.getTrustManagers()).thenReturn(new TrustManager[] { new MyTrustManager() });

        assertThatIllegalStateException()
                .isThrownBy(() -> TrustManagerUtils.getTrustManager(factory))
                .withMessage("Default X509TrustManager not found");
    }
}
