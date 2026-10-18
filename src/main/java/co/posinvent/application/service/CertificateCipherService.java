package co.posinvent.application.service;

import co.posinvent.infrastructure.config.CertificateCryptoProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts sensitive digital-certificate material at rest with AES-256-GCM.
 *
 * <p>Stored values use the {@code v1:} prefix followed by Base64 of
 * {@code IV || ciphertext || authentication tag}. A fresh random 12-byte IV is
 * generated for every encryption, so equal plaintexts never produce equal
 * ciphertexts.</p>
 */
@Service
public class CertificateCipherService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFIX = "v1:";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String INVALID_KEY_MESSAGE =
            "CERT_SECRET_KEY must be Base64-encoded 32 bytes (AES-256)";

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CertificateCipherService(CertificateCryptoProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.secretKey()), "AES");
    }

    public String encrypt(String plaintext) {
        try {
            var iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            var payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt certificate password", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Encrypted certificate password has an unsupported format");
        }

        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Encrypted certificate password is not valid Base64", e);
        }

        if (payload.length <= IV_LENGTH_BYTES) {
            throw new IllegalStateException("Encrypted certificate password is too short");
        }

        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH_BYTES));
            var plaintext = cipher.doFinal(payload, IV_LENGTH_BYTES, payload.length - IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt certificate password", e);
        }
    }

    private static byte[] decodeKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(INVALID_KEY_MESSAGE);
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secretKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(INVALID_KEY_MESSAGE, e);
        }

        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(INVALID_KEY_MESSAGE);
        }
        return decoded;
    }
}
