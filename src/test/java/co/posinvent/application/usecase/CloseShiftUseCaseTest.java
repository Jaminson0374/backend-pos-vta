package co.posinvent.application.usecase;

import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.domain.model.ShiftClosedAccountedEvent;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ShiftRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Audit A2: {@code CloseShiftUseCase} used to sum ONLY {@link SalesDocumentType#INVOICE}
 * for the shift, ignoring POS devoluciones ({@link SalesDocumentType#CREDIT_NOTE}),
 * so any return produced a false shortfall in the Z-Report.
 *
 * <p>Credit notes store a NEGATIVE {@code totalAmount} (see
 * {@code PosDevolutionUseCase}, which negates each returned item's
 * quantity/subtotal/tax). The expected shift total is therefore
 * {@code invoiced + creditNoteTotal} == invoiced - returned.</p>
 */
@ExtendWith(MockitoExtension.class)
class CloseShiftUseCaseTest {

    private static final UUID SHIFT_ID = UUID.randomUUID();
    private static final UUID CASH_REGISTER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private ShiftRepository shiftRepository;
    @Mock private SalesDocumentRepository documentRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CurrentActorProvider actorProvider;
    @Mock private ShiftAccessGuard accessGuard;

    private CloseShiftUseCase useCase;
    private Shift openShift;
    private CurrentActorProvider.CurrentActor actor;

    @BeforeEach
    void setUp() {
        actor = new CurrentActorProvider.CurrentActor(USER_ID, "CAJERO");
        lenient().when(actorProvider.current()).thenReturn(actor);
        useCase = new CloseShiftUseCase(
                shiftRepository, documentRepo, eventPublisher, actorProvider, accessGuard);
        openShift = new Shift(
                SHIFT_ID, CASH_REGISTER_ID, USER_ID, null, null,
                OffsetDateTime.now().minusHours(8), null,
                BigDecimal.ZERO, null, ShiftStatus.OPEN, null,
                OffsetDateTime.now().minusHours(8));
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(openShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void subtractsCreditNotesFromExpectedTotalAndReportsReturnedAmount() {
        var invoice = document(SalesDocumentType.INVOICE, new BigDecimal("100000"));
        var creditNote = document(SalesDocumentType.CREDIT_NOTE, new BigDecimal("-30000"));

        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.INVOICE))
                .thenReturn(List.of(invoice));
        // lenient: before the fix the use case never queried credit notes, which is
        // exactly the bug this test pins down.
        lenient().when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of(creditNote));

        var cashCount = new CashCountRequest(
                new BigDecimal("70000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        var response = useCase.execute(SHIFT_ID, cashCount);

        // expectedTotal = 100000 invoiced - 30000 returned = 70000, matching the cash count.
        assertThat(response.zReportUrl()).contains("Total esp.: " + money(new BigDecimal("70000")));
        assertThat(response.zReportUrl()).contains("Devoluciones: 1");
        assertThat(response.zReportUrl()).contains(money(new BigDecimal("30000")));
        assertThat(response.zReportUrl()).contains("Estado: CUADRE PERFECTO");
    }

    @Test
    void keepsExpectedTotalEqualToInvoicesWhenThereAreNoCreditNotes() {
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.INVOICE))
                .thenReturn(List.of(
                        document(SalesDocumentType.INVOICE, new BigDecimal("30000")),
                        document(SalesDocumentType.INVOICE, new BigDecimal("20000"))));
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        var cashCount = new CashCountRequest(
                new BigDecimal("50000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        var response = useCase.execute(SHIFT_ID, cashCount);

        assertThat(response.zReportUrl()).contains("Facturas:   2");
        assertThat(response.zReportUrl()).contains("Total esp.: " + money(new BigDecimal("50000")));
        assertThat(response.zReportUrl()).contains("Estado: CUADRE PERFECTO");
    }

    @Test
    void generatedZReportExceedsLegacyVarchar500Limit() {
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.INVOICE))
                .thenReturn(List.of(document(SalesDocumentType.INVOICE, new BigDecimal("1250000"))));
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        var cashCount = new CashCountRequest(
                new BigDecimal("1250000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        var response = useCase.execute(SHIFT_ID, cashCount);

        // The old shifts.z_report_url VARCHAR(500) truncated this ~1 KB report.
        assertThat(response.zReportUrl().length()).isGreaterThan(500);
    }

    @Test
    void publishesShiftClosedEventWithPositiveDifferenceOnShortage() {
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.INVOICE))
                .thenReturn(List.of(document(SalesDocumentType.INVOICE, new BigDecimal("100000"))));
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        var cashCount = new CashCountRequest(
                new BigDecimal("90000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        useCase.execute(SHIFT_ID, cashCount);

        var captor = org.mockito.ArgumentCaptor.forClass(ShiftClosedAccountedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.shiftId()).isEqualTo(SHIFT_ID);
        assertThat(event.difference()).isEqualByComparingTo("10000");
        assertThat(event.entryDate()).isNotNull();
    }

    @Test
    void doesNotPublishShiftClosedEventWhenDifferenceIsZero() {
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.INVOICE))
                .thenReturn(List.of(document(SalesDocumentType.INVOICE, new BigDecimal("50000"))));
        when(documentRepo.findByShiftIdAndType(SHIFT_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        var cashCount = new CashCountRequest(
                new BigDecimal("50000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        useCase.execute(SHIFT_ID, cashCount);

        verify(eventPublisher, never()).publishEvent(any(ShiftClosedAccountedEvent.class));
    }

    private static SalesDocument document(SalesDocumentType type, BigDecimal total) {
        return new SalesDocument(
                UUID.randomUUID(), type, SalesDocumentStatus.ISSUED,
                "DOC", null, null, SHIFT_ID, CASH_REGISTER_ID, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                total, USER_ID, OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(), null, false, null);
    }

    /** Mirrors the currency format used by the Z-Report, locale-independent. */
    private static String money(BigDecimal amount) {
        return String.format("COP $%,.0f", amount);
    }
}
