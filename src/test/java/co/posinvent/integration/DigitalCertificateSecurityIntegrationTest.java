package co.posinvent.integration;

import co.posinvent.application.dto.DigitalCertificateResponse;
import co.posinvent.application.service.CertificateCipherService;
import co.posinvent.application.usecase.ManageDigitalCertificateUseCase;
import co.posinvent.domain.repository.DigitalCertificateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3: the admin certificate surface must never leak the PKCS12 bytes or the
 * keystore password, and the password must be encrypted with AES-256-GCM at rest.
 */
class DigitalCertificateSecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String KEYSTORE_PASSWORD = "changeit";

    @Autowired
    private ManageDigitalCertificateUseCase manageDigitalCertificateUseCase;

    @Autowired
    private DigitalCertificateRepository repository;

    @Autowired
    private CertificateCipherService certificateCipherService;

    @Test
    void uploadStoresEncryptedPasswordAndReturnsMetadataOnly() throws Exception {
        byte[] pkcs12 = readFixture();

        var response = manageDigitalCertificateUseCase.upload(pkcs12, KEYSTORE_PASSWORD, "Test Cert");
        try {
            assertThat(response.id()).isNotNull();
            assertThat(response.alias()).isEqualTo("Test Cert");
            assertThat(response.issuer()).contains("PosInVent");
            assertThat(response.subject()).contains("Test Certificate");
            assertThat(response.expirationDate()).isNotNull().isAfter(LocalDate.now());
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.uploadedAt()).isNotNull();

            assertResponseExposesNoSensitiveFields();

            var persisted = repository.findById(response.id()).orElseThrow();
            assertThat(persisted.passwordEncrypted()).startsWith("v1:");
            assertThat(persisted.passwordEncrypted())
                    .isNotEqualTo(Base64.getEncoder().encodeToString(KEYSTORE_PASSWORD.getBytes()));
            assertThat(certificateCipherService.decrypt(persisted.passwordEncrypted()))
                    .isEqualTo(KEYSTORE_PASSWORD);
            assertThat(persisted.issuer()).contains("PosInVent");
            assertThat(persisted.subject()).contains("Test Certificate");
            assertThat(persisted.certificateData()).isEqualTo(pkcs12);
        } finally {
            manageDigitalCertificateUseCase.delete(response.id());
        }
    }

    private void assertResponseExposesNoSensitiveFields() {
        var components = DigitalCertificateResponse.class.getRecordComponents();
        assertThat(components)
                .extracting(RecordComponent::getName)
                .doesNotContain("certificateData", "passwordEncrypted", "password");
        assertThat(components)
                .extracting(RecordComponent::getType)
                .doesNotContain(byte[].class);
    }

    private byte[] readFixture() throws Exception {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("certs/test-certificate.p12")) {
            assertThat(input).as("test PKCS12 fixture").isNotNull();
            return input.readAllBytes();
        }
    }
}
