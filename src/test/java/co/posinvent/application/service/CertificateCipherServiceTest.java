package co.posinvent.application.service;

import co.posinvent.infrastructure.config.CertificateCryptoProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateCipherServiceTest {

    private static final String KEY_BASE64 = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
    private static final String OTHER_KEY_BASE64 = "YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmI=";

    private CertificateCipherService serviceWith(String key) {
        return new CertificateCipherService(new CertificateCryptoProperties(key));
    }

    @Test
    void encryptsAndDecryptsRoundTrip() {
        var service = serviceWith(KEY_BASE64);
        var plaintext = "s3cr3t-keystore-password";

        var stored = service.encrypt(plaintext);

        assertThat(stored).startsWith("v1:");
        assertThat(service.decrypt(stored)).isEqualTo(plaintext);
    }

    @Test
    void usesRandomIvSoCiphertextsDiffer() {
        var service = serviceWith(KEY_BASE64);
        var plaintext = "same-password";

        var first = service.encrypt(plaintext);
        var second = service.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo(plaintext);
        assertThat(service.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void rejectsTamperedCiphertext() {
        var service = serviceWith(KEY_BASE64);
        var stored = service.encrypt("changeit");
        var payload = Base64.getDecoder().decode(stored.substring("v1:".length()));
        payload[payload.length - 1] ^= 0x01;
        var tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsFastWhenKeyIsNotValidBase64() {
        assertThatThrownBy(() -> serviceWith("not-base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CERT_SECRET_KEY must be Base64-encoded 32 bytes (AES-256)");
    }

    @Test
    void failsFastWhenKeyIsNot32Bytes() {
        var shortKey = Base64.getEncoder()
                .encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> serviceWith(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CERT_SECRET_KEY must be Base64-encoded 32 bytes (AES-256)");
    }

    @Test
    void cannotDecryptWithDifferentKey() {
        var stored = serviceWith(KEY_BASE64).encrypt("changeit");

        assertThatThrownBy(() -> serviceWith(OTHER_KEY_BASE64).decrypt(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsupportedStorageFormat() {
        assertThatThrownBy(() -> serviceWith(KEY_BASE64).decrypt("plain-text"))
                .isInstanceOf(IllegalStateException.class);
    }
}
