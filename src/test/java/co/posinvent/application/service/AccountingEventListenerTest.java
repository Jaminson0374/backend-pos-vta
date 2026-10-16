package co.posinvent.application.service;

import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.AccountingTemplate;
import co.posinvent.domain.model.AccountingTemplateEntry;
import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.InvoiceIssuedEvent;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.model.SaleItem;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the COGS-from-Kardex mapping in {@link AccountingEventListener}.
 *
 * <p>Proves that SALE_COGS / SALE_INVENTORY_OUT are valued at the real Kardex cost
 * (quantity × unitCost, aggregated per product across the sale's EXIT movements) and
 * that a product absent from the Kardex is skipped rather than valued at its sale price.</p>
 */
@ExtendWith(MockitoExtension.class)
class AccountingEventListenerTest {

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CREDIT_NOTE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    private static final UUID ACCT_RECEIVABLE = UUID.randomUUID();
    private static final UUID ACCT_INCOME = UUID.randomUUID();
    private static final UUID ACCT_COGS = UUID.randomUUID();
    private static final UUID ACCT_INVENTORY = UUID.randomUUID();
    private static final UUID ACCT_TAX = UUID.randomUUID();

    private static final UUID PRODUCT_WITH_COST = UUID.randomUUID();
    private static final UUID PRODUCT_WITHOUT_COST = UUID.randomUUID();

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
        when(templateResolver.resolveDefault("SALE")).thenReturn(Optional.of(saleTemplate()));
        when(templateResolver.resolveForProduct(any(), eq("SALE"))).thenReturn(Optional.empty());
        when(pucRepo.findById(any())).thenAnswer(inv ->
                Optional.of(new PucAccount(inv.getArgument(0), "0000", "Cuenta", 1, null, 1,
                        "DEBITO", true, true, null, null)));
    }

    @Test
    void valuesCogsAtKardexCostAndSkipsProductsWithoutCost() {
        var doc = invoiceWithItems();
        when(salesDocumentRepo.findByIdWithItems(INVOICE_ID)).thenReturn(Optional.of(doc));

        // PRODUCT_WITH_COST is picked across two batches (1 + 1) → cost aggregated to 2 × 3,000 = 6,000.
        // A non-EXIT movement must be ignored. PRODUCT_WITHOUT_COST has no movement.
        when(kardexRepo.findByReference("SALE", ORDER_ID)).thenReturn(List.of(
                exit(PRODUCT_WITH_COST, new BigDecimal("1"), new BigDecimal("3000.00")),
                exit(PRODUCT_WITH_COST, new BigDecimal("1"), new BigDecimal("3000.00")),
                entry(PRODUCT_WITH_COST, new BigDecimal("5"), new BigDecimal("3000.00"))
        ));

        listener.onInvoiceIssued(new InvoiceIssuedEvent(
                this, INVOICE_ID, "FV-TEST-1",
                new BigDecimal("25000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("25000.00")));

        var lines = captureLines();

        // COGS / inventory-out use the Kardex cost, not the sale price (PRODUCT_WITH_COST subtotal = 20,000).
        assertThat(amountOn(lines, ACCT_COGS)).isEqualByComparingTo("6000.00");
        assertThat(amountOn(lines, ACCT_INVENTORY)).isEqualByComparingTo("6000.00");

        // One COGS line and one inventory line only: PRODUCT_WITHOUT_COST was skipped.
        assertThat(lines).filteredOn(l -> ACCT_COGS.equals(l.accountId())).hasSize(1);
        assertThat(lines).filteredOn(l -> ACCT_INVENTORY.equals(l.accountId())).hasSize(1);

        // The entry stays balanced (debits == credits).
        assertThat(totalDebit(lines)).isEqualByComparingTo(totalCredit(lines));

        // The movements are looked up under the sale's source order id.
        verify(kardexRepo).findByReference("SALE", ORDER_ID);
    }

    @Test
    void creditNoteEventPostsReversedPositiveLines() {
        var doc = creditNoteWithItems();
        when(salesDocumentRepo.findByIdWithItems(CREDIT_NOTE_ID)).thenReturn(Optional.of(doc));

        // The devolution re-enters the returned goods: ENTRY movements at the Kardex cost.
        when(kardexRepo.findByReference("SALE", CREDIT_NOTE_ID)).thenReturn(List.of(
                entry(PRODUCT_WITH_COST, new BigDecimal("2"), new BigDecimal("3000.00"))
        ));

        // A credit note publishes negative totals; the reversal must not post them verbatim.
        listener.onInvoiceIssued(new InvoiceIssuedEvent(
                this, CREDIT_NOTE_ID, "NC-TEST-1",
                new BigDecimal("-20000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-20000.00")));

        var lines = captureLines(CREDIT_NOTE_ID);

        // Every line is single-sided, positive and non-zero — the journal_entry_lines CHECK.
        assertThat(lines).isNotEmpty().allSatisfy(line -> {
            assertThat(line.debit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(line.credit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(line.debit().add(line.credit())).isGreaterThan(BigDecimal.ZERO);
            assertThat(line.debit().signum() == 0 || line.credit().signum() == 0).isTrue();
        });

        // Natures are crossed relative to the sale: receivable is credited |total| and income
        // is debited |subtotal|.
        assertThat(creditOn(lines, ACCT_RECEIVABLE)).isEqualByComparingTo("20000.00");
        assertThat(debitOn(lines, ACCT_INCOME)).isEqualByComparingTo("20000.00");

        // The inventory pair is swapped and valued at the real Kardex cost (2 × 3,000).
        assertThat(creditOn(lines, ACCT_COGS)).isEqualByComparingTo("6000.00");
        assertThat(debitOn(lines, ACCT_INVENTORY)).isEqualByComparingTo("6000.00");
        assertThat(lines).filteredOn(l -> ACCT_COGS.equals(l.accountId())).hasSize(1);
        assertThat(lines).filteredOn(l -> ACCT_INVENTORY.equals(l.accountId())).hasSize(1);

        // The reversal entry is balanced.
        assertThat(totalDebit(lines)).isEqualByComparingTo(totalCredit(lines));

        // The returned cost is looked up under the credit note id.
        verify(kardexRepo).findByReference("SALE", CREDIT_NOTE_ID);
    }

    @Test
    void creditNoteReversalDebitsPerRateTax() {
        when(templateResolver.resolveDefault("SALE")).thenReturn(Optional.of(saleTemplateWithTax()));
        when(pucRepo.findById(ACCT_TAX)).thenReturn(Optional.of(
                new PucAccount(ACCT_TAX, "240815", "IVA 19", 1, null, 1,
                        "CREDITO", true, true, null, null)));

        var doc = creditNoteWithIva19();
        when(salesDocumentRepo.findByIdWithItems(CREDIT_NOTE_ID)).thenReturn(Optional.of(doc));
        when(kardexRepo.findByReference("SALE", CREDIT_NOTE_ID)).thenReturn(List.of(
                entry(PRODUCT_WITH_COST, BigDecimal.ONE, new BigDecimal("3000.00"))
        ));

        // net -10,000; IVA 19% -1,900; total -11,900.
        listener.onInvoiceIssued(new InvoiceIssuedEvent(
                this, CREDIT_NOTE_ID, "NC-IVA-1",
                new BigDecimal("-10000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1900.00"),
                new BigDecimal("-11900.00")));

        var lines = captureLines(CREDIT_NOTE_ID);

        assertThat(lines).allSatisfy(line -> {
            assertThat(line.debit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(line.credit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(line.debit().add(line.credit())).isGreaterThan(BigDecimal.ZERO);
        });

        // Tax is reversed to the debit side at its absolute value (the sale credited it).
        assertThat(debitOn(lines, ACCT_TAX)).isEqualByComparingTo("1900.00");
        assertThat(debitOn(lines, ACCT_INCOME)).isEqualByComparingTo("10000.00");
        assertThat(creditOn(lines, ACCT_RECEIVABLE)).isEqualByComparingTo("11900.00");

        // Cost pair swapped at the Kardex cost (1 × 3,000).
        assertThat(creditOn(lines, ACCT_COGS)).isEqualByComparingTo("3000.00");
        assertThat(debitOn(lines, ACCT_INVENTORY)).isEqualByComparingTo("3000.00");

        assertThat(totalDebit(lines)).isEqualByComparingTo(totalCredit(lines));
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private List<JournalEntryLine> captureLines() {
        return captureLines(INVOICE_ID);
    }

    private List<JournalEntryLine> captureLines(UUID sourceId) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalEntryLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalUseCase).createAuto(eq("SALE"), eq(sourceId), any(LocalDate.class), anyString(), captor.capture());
        return captor.getValue();
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

    private BigDecimal amountOn(List<JournalEntryLine> lines, UUID accountId) {
        return lines.stream()
                .filter(l -> accountId.equals(l.accountId()))
                .map(l -> l.debit().compareTo(BigDecimal.ZERO) > 0 ? l.debit() : l.credit())
                .findFirst().orElseThrow();
    }

    private BigDecimal totalDebit(List<JournalEntryLine> lines) {
        return lines.stream().map(JournalEntryLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalCredit(List<JournalEntryLine> lines) {
        return lines.stream().map(JournalEntryLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private AccountingTemplate saleTemplate() {
        return new AccountingTemplate(TEMPLATE_ID, "DEFAULT_SALE", "Venta", null, "SALE", true, true,
                List.of(
                        new AccountingTemplateEntry(UUID.randomUUID(), TEMPLATE_ID, "SALE_RECEIVABLE", ACCT_RECEIVABLE, true, 1),
                        new AccountingTemplateEntry(UUID.randomUUID(), TEMPLATE_ID, "SALE_INCOME", ACCT_INCOME, false, 2),
                        new AccountingTemplateEntry(UUID.randomUUID(), TEMPLATE_ID, "SALE_COGS", ACCT_COGS, true, 3),
                        new AccountingTemplateEntry(UUID.randomUUID(), TEMPLATE_ID, "SALE_INVENTORY_OUT", ACCT_INVENTORY, false, 4)
                ), null, null);
    }

    private AccountingTemplate saleTemplateWithTax() {
        return new AccountingTemplate(TEMPLATE_ID, "DEFAULT_SALE", "Venta", null, "SALE", true, true,
                List.of(
                        new AccountingTemplateEntry(UUID.randomUUID(), TEMPLATE_ID, "SALE_RECEIVABLE", ACCT_RECEIVABLE, true, 1),
                        new AccountingTemplateEntry(UUID.randomUUID(), TEMPLATE_ID, "SALE_INCOME", ACCT_INCOME, false, 2),
                        new AccountingTemplateEntry(UUID.randomUUID(), TEMPLATE_ID, "SALE_COGS", ACCT_COGS, true, 3),
                        new AccountingTemplateEntry(UUID.randomUUID(), TEMPLATE_ID, "SALE_INVENTORY_OUT", ACCT_INVENTORY, false, 4),
                        new AccountingTemplateEntry(UUID.randomUUID(), TEMPLATE_ID, "SALE_TAX", ACCT_TAX, false, 5)
                ), null, null);
    }

    private SalesDocument invoiceWithItems() {
        var withCost = new SaleItem(null, INVOICE_ID, PRODUCT_WITH_COST, new BigDecimal("2"),
                new BigDecimal("10000.00"), "EXENTO", BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("20000.00"), 1, UUID.randomUUID());
        var withoutCost = new SaleItem(null, INVOICE_ID, PRODUCT_WITHOUT_COST, BigDecimal.ONE,
                new BigDecimal("5000.00"), "EXENTO", BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("5000.00"), 2, UUID.randomUUID());

        return new SalesDocument(
                INVOICE_ID, SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED, "FV-TEST-1",
                CLIENT_ID, WAREHOUSE_ID, null, null, ORDER_ID,
                new BigDecimal("25000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("25000.00"), USER_ID, null, null,
                List.of(withCost, withoutCost), null, false, null);
    }

    private SalesDocument creditNoteWithItems() {
        // A credit note mirrors the invoice with negative quantities and totals.
        var item = new SaleItem(null, CREDIT_NOTE_ID, PRODUCT_WITH_COST, new BigDecimal("-2"),
                new BigDecimal("10000.00"), "EXENTO", BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-20000.00"), 1, UUID.randomUUID());

        return new SalesDocument(
                CREDIT_NOTE_ID, SalesDocumentType.CREDIT_NOTE, SalesDocumentStatus.ISSUED, "NC-TEST-1",
                CLIENT_ID, WAREHOUSE_ID, null, null, null,
                new BigDecimal("-20000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-20000.00"), USER_ID, null, null,
                List.of(item), null, false, null);
    }

    private SalesDocument creditNoteWithIva19() {
        var item = new SaleItem(null, CREDIT_NOTE_ID, PRODUCT_WITH_COST, BigDecimal.ONE.negate(),
                new BigDecimal("10000.00"), "IVA_19", new BigDecimal("19"), new BigDecimal("-1900.00"),
                new BigDecimal("-10000.00"), 1, UUID.randomUUID());

        return new SalesDocument(
                CREDIT_NOTE_ID, SalesDocumentType.CREDIT_NOTE, SalesDocumentStatus.ISSUED, "NC-IVA-1",
                CLIENT_ID, WAREHOUSE_ID, null, null, null,
                new BigDecimal("-10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1900.00"),
                new BigDecimal("-11900.00"), USER_ID, null, null,
                List.of(item), null, false, null);
    }

    private InventoryMovement exit(UUID productId, BigDecimal quantity, BigDecimal unitCost) {
        return new InventoryMovement(UUID.randomUUID(), productId, UUID.randomUUID(), WAREHOUSE_ID,
                MovementType.EXIT, quantity, unitCost, BigDecimal.TEN, BigDecimal.TEN.subtract(quantity),
                "SALE", ORDER_ID, null, "SYSTEM", OffsetDateTime.now());
    }

    private InventoryMovement entry(UUID productId, BigDecimal quantity, BigDecimal unitCost) {
        return new InventoryMovement(UUID.randomUUID(), productId, UUID.randomUUID(), WAREHOUSE_ID,
                MovementType.ENTRY, quantity, unitCost, BigDecimal.ZERO, quantity,
                "SALE", CREDIT_NOTE_ID, null, "SYSTEM", OffsetDateTime.now());
    }
}
