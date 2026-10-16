package co.posinvent.application.usecase;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.ElectronicInvoice;
import co.posinvent.domain.model.ElectronicInvoiceStatus;
import co.posinvent.domain.repository.ElectronicInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read path used by the POS DIAN-status endpoint. When the asynchronous
 * DIAN job has not yet inserted the {@code PENDING_SEND} row, the lookup must raise
 * {@link ResourceNotFoundException} (mapped to 404) so the cashier can poll cleanly.
 */
@ExtendWith(MockitoExtension.class)
class ManageElectronicInvoiceUseCaseTest {

    @Mock
    private ElectronicInvoiceRepository repository;

    private ManageElectronicInvoiceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageElectronicInvoiceUseCase(repository);
    }

    private static ElectronicInvoice invoice(UUID salesDocumentId) {
        return new ElectronicInvoice(
                UUID.randomUUID(),
                salesDocumentId,
                null,
                "CUFE-TEST-123",
                "qr-payload-test",
                Map.of(),
                ElectronicInvoiceStatus.PENDING_SEND,
                null,
                null,
                OffsetDateTime.now());
    }

    @Test
    void getBySalesDocument_returnsMappedResponseWhenPresent() {
        var salesDocumentId = UUID.randomUUID();
        when(repository.findBySalesDocumentId(salesDocumentId))
                .thenReturn(Optional.of(invoice(salesDocumentId)));

        var result = useCase.getBySalesDocument(salesDocumentId);

        assertThat(result.salesDocumentId()).isEqualTo(salesDocumentId);
        assertThat(result.cufe()).isEqualTo("CUFE-TEST-123");
        assertThat(result.qrCode()).isEqualTo("qr-payload-test");
        assertThat(result.status()).isEqualTo(ElectronicInvoiceStatus.PENDING_SEND);
    }

    @Test
    void getBySalesDocument_throwsResourceNotFoundWhenAbsent() {
        var salesDocumentId = UUID.randomUUID();
        when(repository.findBySalesDocumentId(salesDocumentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getBySalesDocument(salesDocumentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
