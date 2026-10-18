package co.posinvent.application.usecase;

import co.posinvent.application.dto.DigitalCertificateResponse;
import co.posinvent.application.service.CertificateCipherService;
import co.posinvent.domain.exception.InvalidUploadException;
import co.posinvent.domain.model.DigitalCertificate;
import co.posinvent.domain.repository.DigitalCertificateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ManageDigitalCertificateUseCase {

    private final DigitalCertificateRepository repository;
    private final CertificateCipherService cipherService;

    public ManageDigitalCertificateUseCase(
            DigitalCertificateRepository repository,
            CertificateCipherService cipherService) {
        this.repository = repository;
        this.cipherService = cipherService;
    }

    @Transactional(readOnly = true)
    public List<DigitalCertificateResponse> listAll() {
        return repository.findAll().stream()
                .map(DigitalCertificateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DigitalCertificateResponse getById(UUID id) {
        return DigitalCertificateResponse.from(findOrThrow(id));
    }

    @Transactional
    public DigitalCertificateResponse upload(byte[] fileBytes, String password, String fileName) {
        var metadata = extractMetadata(fileBytes, password);
        var encryptedPassword = password != null ? cipherService.encrypt(password) : null;

        var certificate = new DigitalCertificate(
                null,
                fileName != null ? fileName : "certificate",
                metadata.issuer(),
                metadata.subject(),
                fileBytes,
                encryptedPassword,
                metadata.validUntil(),
                true,
                null
        );

        return DigitalCertificateResponse.from(repository.save(certificate));
    }

    @Transactional
    public void delete(UUID id) {
        if (repository.findById(id).isEmpty()) {
            throw new co.posinvent.domain.exception.ResourceNotFoundException("Certificado", id);
        }
        repository.deleteById(id);
    }

    private DigitalCertificate findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new co.posinvent.domain.exception.ResourceNotFoundException("Certificado", id));
    }

    /**
     * Reads the PKCS12 once and returns every metadata value the API exposes.
     *
     * <p>Uploads are rejected with {@code INVALID_CERTIFICATE} when the keystore
     * cannot be opened (wrong password, malformed bytes, or a parse/cast failure)
     * or when it holds no X509 certificate. There is no silent fallback: storing a
     * certificate with fabricated validity or unknown issuer/subject would make
     * the admin surface lie about the real certificate.</p>
     */
    private CertificateMetadata extractMetadata(byte[] fileBytes, String password) {
        try (var inputStream = new ByteArrayInputStream(fileBytes)) {
            var keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(inputStream, password != null ? password.toCharArray() : new char[0]);

            var aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                var alias = aliases.nextElement();
                if (keyStore.getCertificate(alias) instanceof X509Certificate certificate) {
                    var validUntil = Instant.ofEpochMilli(certificate.getNotAfter().getTime())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    return new CertificateMetadata(
                            validUntil,
                            certificate.getIssuerX500Principal().getName(),
                            certificate.getSubjectX500Principal().getName()
                    );
                }
            }
        } catch (IOException | GeneralSecurityException | ClassCastException e) {
            throw invalidCertificate();
        }
        throw invalidCertificate();
    }

    private static InvalidUploadException invalidCertificate() {
        return new InvalidUploadException(
                "INVALID_CERTIFICATE",
                "El archivo no es un PKCS12 válido o la contraseña es incorrecta.");
    }

    private record CertificateMetadata(LocalDate validUntil, String issuer, String subject) {}
}
