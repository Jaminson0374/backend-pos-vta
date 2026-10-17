package co.posinvent.application.service;

import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.AccountingTemplate;
import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.model.SupplierAdvanceAppliedEvent;
import co.posinvent.domain.model.SupplierAdvanceCreatedEvent;
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
 * Unit coverage for the supplier-advance mapping in {@link AccountingEventListener} (S3b).
 *
 * <p>Creating an unapplied advance moves cash into the {@code 133005} asset
 * (debit asset / credit cash). Applying it against an invoice crosses the asset against the
 * payable (debit {@code 2205} / credit {@code 133005}). Both entries preserve the document date,
 * skip when journal generation is disabled and skip (with a warning) when an account is missing.</p>
 */
@ExtendWith(MockitoExtension.class)
class SupplierAdvanceListenerTest {

    private static final UUID ADVANCE_ID = UUID.randomUUID();
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID ACCT_ASSET = UUID.randomUUID();
    private static final UUID ACCT_CASH = UUID.randomUUID();
    private static final UUID ACCT_PAYABLE = UUID.randomUUID();

    private static final BigDecimal AMOUNT = new BigDecimal("1000.00");
    private static final LocalDate ADVANCE_DATE = LocalDate.of(2024, 5, 10);
    private static final LocalDate APPLICATION_DATE = LocalDate.of(2024, 5, 25);

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
    void createdAdvancePostsAssetDebitAndCashCreditDatedWithTheAdvance() {
        when(pucRepo.findByCode("133005")).thenReturn(Optional.of(account(ACCT_ASSET, "133005")));
        when(pucRepo.findByCode("1105")).thenReturn(Optional.of(account(ACCT_CASH, "1105")));

        listener.onSupplierAdvanceCreated(new SupplierAdvanceCreatedEvent(
                this, ADVANCE_ID, "ANT-1", AMOUNT, ADVANCE_DATE));

        var lines = captureLines("ADVANCE", ADVANCE_ID);
        assertThat(debitOn(lines, ACCT_ASSET)).isEqualByComparingTo(AMOUNT);
        assertThat(creditOn(lines, ACCT_CASH)).isEqualByComparingTo(AMOUNT);
        assertThat(totalDebit(lines)).isEqualByComparingTo(totalCredit(lines));

        var dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(journalUseCase).createAuto(
                eq("ADVANCE"), eq(ADVANCE_ID), dateCaptor.capture(), anyString(), any());
        assertThat(dateCaptor.getValue()).isEqualTo(ADVANCE_DATE);
    }

    @Test
    void appliedAdvancePostsPayableDebitAndAssetCredit() {
        // No PURCHASE template → the payable falls back to PUC code 2205.
        when(templateResolver.resolveDefault("PURCHASE")).thenReturn(Optional.empty());
        when(pucRepo.findByCode("2205")).thenReturn(Optional.of(account(ACCT_PAYABLE, "2205")));
        when(pucRepo.findByCode("133005")).thenReturn(Optional.of(account(ACCT_ASSET, "133005")));

        listener.onSupplierAdvanceApplied(new SupplierAdvanceAppliedEvent(
                this, APPLICATION_ID, ADVANCE_ID, INVOICE_ID, AMOUNT, APPLICATION_DATE));

        var lines = captureLines("ADVANCE_APPLICATION", APPLICATION_ID);
        assertThat(debitOn(lines, ACCT_PAYABLE)).isEqualByComparingTo(AMOUNT);
        assertThat(creditOn(lines, ACCT_ASSET)).isEqualByComparingTo(AMOUNT);
        assertThat(totalDebit(lines)).isEqualByComparingTo(totalCredit(lines));

        var dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(journalUseCase).createAuto(
                eq("ADVANCE_APPLICATION"), eq(APPLICATION_ID), dateCaptor.capture(), anyString(), any());
        assertThat(dateCaptor.getValue()).isEqualTo(APPLICATION_DATE);
    }

    @Test
    void skipsTheEntryWhenJournalGenerationIsDisabled() {
        when(companyConfigRepo.findConfig()).thenReturn(Optional.of(disabledConfig()));

        listener.onSupplierAdvanceCreated(new SupplierAdvanceCreatedEvent(
                this, ADVANCE_ID, "ANT-2", AMOUNT, ADVANCE_DATE));

        verify(journalUseCase, never()).createAuto(any(), any(), any(), any(), any());
    }

    @Test
    void missingAdvanceAssetAccountWarnsAndSkipsTheEntry() {
        when(pucRepo.findByCode(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> listener.onSupplierAdvanceCreated(new SupplierAdvanceCreatedEvent(
                this, ADVANCE_ID, "ANT-3", AMOUNT, ADVANCE_DATE)))
                .doesNotThrowAnyException();

        verify(journalUseCase, never()).createAuto(any(), any(), any(), any(), any());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<JournalEntryLine> captureLines(String sourceType, UUID sourceId) {
        ArgumentCaptor<List<JournalEntryLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalUseCase).createAuto(
                eq(sourceType), eq(sourceId), any(LocalDate.class), anyString(), captor.capture());
        return captor.getValue();
    }

    private PucAccount account(UUID id, String code) {
        return new PucAccount(id, code, "Cuenta", 1, null, 1, "DEBITO", true, true, null, null);
    }

    private CompanyConfig disabledConfig() {
        return new CompanyConfig(
                1L, "Test Company", "900111222", "Calle 1", "3001234567", "test@example.com",
                "4711", "JURIDICA", null, false,
                List.of(), List.of(), List.of(),
                BigDecimal.ZERO, "COP", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                false, BigDecimal.ZERO, null, null);
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
