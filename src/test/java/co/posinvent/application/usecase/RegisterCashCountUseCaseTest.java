package co.posinvent.application.usecase;

import co.posinvent.application.dto.CashCountLineRequest;
import co.posinvent.application.dto.RegisterCashCountRequest;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.CashCount;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.domain.repository.CashCountRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Audit A4: the POS Arqueo screen counts denominations in memory and never persists
 * them. {@link RegisterCashCountUseCase} turns the counted denominations into a
 * historical {@link CashCount} record: counted total is the sum of
 * (denominationValue * quantity), the expected total is the shift's NET document
 * total (invoices + negative credit notes, mirroring audit A2), and the difference
 * is counted - expected.
 */
@ExtendWith(MockitoExtension.class)
class RegisterCashCountUseCaseTest {

    private static final UUID SHIFT_ID = UUID.randomUUID();
    private static final UUID CASH_REGISTER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private ShiftRepository shiftRepository;
    @Mock private SalesDocumentRepository documentRepo;
    @Mock private CashCountRepository cashCountRepository;

    private RegisterCashCountUseCase useCase;
    private Shift openShift;

    @BeforeEach
    void setUp() {
        useCase = new RegisterCashCountUseCase(shiftRepository, documentRepo, cashCountRepository);
        openShift = new Shift(
                SHIFT_ID, CASH_REGISTER_ID, USER_ID,
                OffsetDateTime.now().minusHours(8), null,
                BigDecimal.ZERO, null, ShiftStatus.OPEN, null,
                OffsetDateTime.now().minusHours(8));
        lenient().when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(openShift));
        lenient().when(cashCountRepository.save(any(CashCount.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.INVOICE))
                .thenReturn(List.of());
    }

    @Test
    void computesCountedTotalFromDenominationLinesAndPersistsBreakdown() {
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        var request = new RegisterCashCountRequest(
                List.of(
                        new CashCountLineRequest(new BigDecimal("50000.00"), "BILLETE", 2),
                        new CashCountLineRequest(new BigDecimal("100.00"), "MONEDA", 5)),
                "Arqueo de prueba");

        var response = useCase.execute(SHIFT_ID, request, USER_ID);

        // counted = 50000*2 + 100*5 = 100500
        assertThat(response.countedTotal()).isEqualByComparingTo("100500.00");
        assertThat(response.expectedTotal()).isEqualByComparingTo("0");
        assertThat(response.difference()).isEqualByComparingTo("100500.00");
        assertThat(response.lines()).hasSize(2);

        var captor = ArgumentCaptor.forClass(CashCount.class);
        verify(cashCountRepository).save(captor.capture());
        var persisted = captor.getValue();
        assertThat(persisted.shiftId()).isEqualTo(SHIFT_ID);
        assertThat(persisted.createdBy()).isEqualTo(USER_ID);
        assertThat(persisted.lines()).hasSize(2);
        assertThat(persisted.lines()).extracting(CashCount.Line::subtotal)
                .containsExactlyInAnyOrder(new BigDecimal("100000.00"), new BigDecimal("500.00"));
    }

    @Test
    void netsCreditNotesIntoExpectedTotalSoDifferenceIsZero() {
        var invoice = document(SalesDocumentType.INVOICE, new BigDecimal("100000.00"));
        var creditNote = document(SalesDocumentType.CREDIT_NOTE, new BigDecimal("-30000.00"));
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.INVOICE))
                .thenReturn(List.of(invoice));
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of(creditNote));

        var request = new RegisterCashCountRequest(
                List.of(new CashCountLineRequest(new BigDecimal("70000.00"), "BILLETE", 1)),
                null);

        var response = useCase.execute(SHIFT_ID, request, USER_ID);

        // expected = 100000 invoiced - 30000 returned = 70000, matching the count.
        assertThat(response.expectedTotal()).isEqualByComparingTo("70000.00");
        assertThat(response.countedTotal()).isEqualByComparingTo("70000.00");
        assertThat(response.difference()).isEqualByComparingTo("0.00");
    }

    @Test
    void throwsWhenShiftDoesNotExist() {
        var missingId = UUID.randomUUID();
        when(shiftRepository.findById(missingId)).thenReturn(Optional.empty());

        var request = new RegisterCashCountRequest(
                List.of(new CashCountLineRequest(new BigDecimal("1000.00"), "BILLETE", 1)),
                null);

        assertThatThrownBy(() -> useCase.execute(missingId, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static SalesDocument document(SalesDocumentType type, BigDecimal total) {
        return new SalesDocument(
                UUID.randomUUID(), type, SalesDocumentStatus.ISSUED,
                "DOC", null, null, SHIFT_ID, CASH_REGISTER_ID, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                total, USER_ID, OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(), null, false, null);
    }
}
