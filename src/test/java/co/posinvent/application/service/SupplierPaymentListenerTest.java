package co.posinvent.application.service;

import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.AccountingTemplate;
import co.posinvent.domain.model.AccountingTemplateEntry;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.model.SupplierPaymentEvent;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.KardexRepository;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the supplier-payment mapping in {@link AccountingEventListener} (WU-36a, C4).
 *
 * <p>Proves the payment posts debit payable / credit cash using the same payable account as the
 * purchase (template {@code PURCHASE_PAYABLE}, else code {@code 2205}), dated with the payment,
 * and that a missing account is warned and skipped instead of posting a broken entry.</p>
 */
@ExtendWith(MockitoExtension.class)
class SupplierPaymentListenerTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final UUID ACCT_PAYABLE = UUID.randomUUID();
    private static final UUID ACCT_CASH = UUID.randomUUID();

    private static final BigDecimal AMOUNT = new BigDecimal("80000.00");
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2024, 6, 20);

    @Mock private CreateJournalEntryUseCase journalUseCase;
    @Mock private PucAccountRepository pucRepo;
    @Mock private TemplateResolverService templateResolver;
    @Mock private CompanyConfigRepository companyConfigRepo;
    @Mock private SalesDocumentRepository salesDocumentRepo;
    @Mock private KardexRepository kardexRepo;

    private AccountingEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AccountingEventListener(
                journalUseCase, pucRepo, templateResolver, companyConfigRepo, salesDocumentRepo, kardexRepo);
        when(companyConfigRepo.findConfig()).thenReturn(Optional.empty()); // journal generation enabled
    }

    @Test
    void mapsPayableDebitAndCashCreditDatedWithThePayment() {
        // No PURCHASE template → the payable falls back to PUC code 2205.
        when(templateResolver.resolveDefault("PURCHASE")).thenReturn(Optional.empty());
        when(pucRepo.findByCode("2205")).thenReturn(Optional.of(account(ACCT_PAYABLE, "2205")));
        when(pucRepo.findByCode("1105")).thenReturn(Optional.of(account(ACCT_CASH, "1105")));

        listener.onSupplierPayment(new SupplierPaymentEvent(
                this, PAYMENT_ID, "PAG-1", AMOUNT, PAYMENT_DATE));

        var lines = captureLines();
        assertThat(debitOn(lines, ACCT_PAYABLE)).isEqualByComparingTo(AMOUNT);
        assertThat(creditOn(lines, ACCT_CASH)).isEqualByComparingTo(AMOUNT);
        assertThat(totalDebit(lines)).isEqualByComparingTo(totalCredit(lines));

        // Dated with the payment and idempotent on the payment id.
        var dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(journalUseCase).createAuto(eq("PAYMENT"), eq(PAYMENT_ID), dateCaptor.capture(), anyString(), any());
        assertThat(dateCaptor.getValue()).isEqualTo(PAYMENT_DATE);
    }

    @Test
    void usesTheTemplatePayableAccountWhenPresent() {
        when(templateResolver.resolveDefault("PURCHASE")).thenReturn(Optional.of(purchaseTemplate()));
        when(pucRepo.findById(ACCT_PAYABLE)).thenReturn(Optional.of(account(ACCT_PAYABLE, "2205")));
        when(pucRepo.findByCode("1105")).thenReturn(Optional.of(account(ACCT_CASH, "1105")));

        listener.onSupplierPayment(new SupplierPaymentEvent(
                this, PAYMENT_ID, "PAG-2", AMOUNT, PAYMENT_DATE));

        var lines = captureLines();
        assertThat(debitOn(lines, ACCT_PAYABLE)).isEqualByComparingTo(AMOUNT);
        assertThat(creditOn(lines, ACCT_CASH)).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void missingPayableAccountWarnsAndSkipsTheEntry() {
        when(templateResolver.resolveDefault("PURCHASE")).thenReturn(Optional.empty());
        when(pucRepo.findByCode(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> listener.onSupplierPayment(new SupplierPaymentEvent(
                this, PAYMENT_ID, "PAG-3", AMOUNT, PAYMENT_DATE)))
                .doesNotThrowAnyException();

        verify(journalUseCase, never()).createAuto(any(), any(), any(), any(), any());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<JournalEntryLine> captureLines() {
        ArgumentCaptor<List<JournalEntryLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalUseCase).createAuto(eq("PAYMENT"), eq(PAYMENT_ID), any(LocalDate.class), anyString(), captor.capture());
        return captor.getValue();
    }

    private PucAccount account(UUID id, String code) {
        return new PucAccount(id, code, "Cuenta", 1, null, 1, "DEBITO", true, true, null, null);
    }

    private AccountingTemplate purchaseTemplate() {
        return new AccountingTemplate(TEMPLATE_ID, "DEFAULT_PURCHASE", "Compra", null, "PURCHASE", true, true,
                List.of(new AccountingTemplateEntry(
                        UUID.randomUUID(), TEMPLATE_ID, "PURCHASE_PAYABLE", ACCT_PAYABLE, false, 2)),
                null, null);
    }

    private BigDecimal debitOn(List<JournalEntryLine> lines, UUID accountId) {
        return lines.stream()
                .filter(l -> accountId.equals(l.accountId()))
                .map(JournalEntryLine::debit)
                .findFirst().orElseThrow();
    }

    private BigDecimal creditOn(List<JournalEntryLine> lines, UUID accountId) {
        return lines.stream()
                .filter(l -> accountId.equals(l.accountId()))
                .map(JournalEntryLine::credit)
                .findFirst().orElseThrow();
    }

    private BigDecimal totalDebit(List<JournalEntryLine> lines) {
        return lines.stream().map(JournalEntryLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalCredit(List<JournalEntryLine> lines) {
        return lines.stream().map(JournalEntryLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
