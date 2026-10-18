package co.posinvent.application.usecase;

import co.posinvent.application.dto.DigitalCertificateResponse;
import co.posinvent.application.service.CertificateCipherService;
import co.posinvent.domain.model.DigitalCertificate;
import co.posinvent.domain.repository.DigitalCertificateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
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
     * A malformed keystore or wrong password falls back to a one-year validity
     * and unknown issuer/subject so uploads are never silently rejected.
     */
    private CertificateMetadata extractMetadata(byte[] fileBytes, String password) {
        try (var inputStream = new ByteArrayInputStream(fileBytes)) {
            var keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(inputStream, password != null ? password.toCharArray() : new char[0]);

            var aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                var alias = aliases.nextElement();
                var certificate = (X509Certificate) keyStore.getCertificate(alias);
                if (certificate != null) {
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
        } catch (Exception e) {
            // Fall through to the safe defaults below.
        }
        return new CertificateMetadata(LocalDate.now().plusYears(1), null, null);
    }

    private record CertificateMetadata(LocalDate validUntil, String issuer, String subject) {}
}
