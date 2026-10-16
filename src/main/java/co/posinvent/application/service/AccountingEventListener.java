package co.posinvent.application.service;

import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.AccountingTemplateEntry;
import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.InvoiceIssuedEvent;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.KardexRepository;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountingEventListener {

    private static final Logger log = LoggerFactory.getLogger(AccountingEventListener.class);

    private final CreateJournalEntryUseCase journalUseCase;
    private final PucAccountRepository pucRepo;
    private final TemplateResolverService templateResolver;
    private final CompanyConfigRepository companyConfigRepo;
    private final SalesDocumentRepository salesDocumentRepo;
    private final KardexRepository kardexRepo;

    public AccountingEventListener(
            CreateJournalEntryUseCase journalUseCase,
            PucAccountRepository pucRepo,
            TemplateResolverService templateResolver,
            CompanyConfigRepository companyConfigRepo,
            SalesDocumentRepository salesDocumentRepo,
            KardexRepository kardexRepo) {
        this.journalUseCase = journalUseCase;
        this.pucRepo = pucRepo;
        this.templateResolver = templateResolver;
        this.companyConfigRepo = companyConfigRepo;
        this.salesDocumentRepo = salesDocumentRepo;
        this.kardexRepo = kardexRepo;
    }

    // ════════════════════════════════════════════════════════════════
    // FACTURA EMITIDA (dispatch por tipo de documento)
    // ════════════════════════════════════════════════════════════════

    /**
     * Posts the accounting for an emitted sales document after its business transaction has
     * committed (WU-34). Running in {@code AFTER_COMMIT} with its own {@code REQUIRES_NEW}
     * transaction keeps a missing PUC account or any other accounting failure from rolling back
     * the sale that was already charged. The failure is logged and swallowed — best-effort posting.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInvoiceIssued(InvoiceIssuedEvent event) {
        try {
            // Guard: skip if auto journal generation is disabled
            if (!isJournalGenerationEnabled()) {
                return;
            }

            // The event carries no document type, so the accounting treatment is decided from the
            // persisted document: an INVOICE posts the sale entry; a CREDIT_NOTE posts the reversal
            // entry that undoes it. Any other type is not accounted here.
            var docOpt = salesDocumentRepo.findByIdWithItems(event.salesDocumentId());
            if (docOpt.isEmpty()) {
                log.warn("InvoiceIssuedEvent for unknown sales document {}; skipping journal entry",
                        event.salesDocumentId());
                return;
            }
            var doc = docOpt.get();

            switch (doc.type()) {
                case INVOICE -> postSaleEntry(event, doc);
                case CREDIT_NOTE -> postCreditNoteReversal(event);
                default -> log.debug("No accounting treatment for sales document type {} (id {}); skipping",
                        doc.type(), event.salesDocumentId());
            }
        } catch (Exception ex) {
            log.error("Accounting failed for sales document {} ({}); the business operation is unaffected",
                    event.salesDocumentId(), event.invoiceNumber(), ex);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // VENTA (INVOICE)
    // ════════════════════════════════════════════════════════════════

    private void postSaleEntry(InvoiceIssuedEvent event, SalesDocument doc) {
        var lines = new ArrayList<JournalEntryLine>();

        // Resolve the default template for SALE module
        var defaultTemplate = templateResolver.resolveDefault("SALE");

        if (defaultTemplate.isPresent()) {
            // ── Template-based journal entries ──

            // 1. Aggregate entries (income, tax, receivable) — skip SALE_TAX for later per-rate processing
            var taxEntries = new ArrayList<AccountingTemplateEntry>();
            for (var entry : defaultTemplate.get().entries()) {
                if ("SALE_TAX".equals(entry.eventType())) {
                    taxEntries.add(entry);
                    continue;
                }
                var account = resolveById(entry.accountId(), entry.eventType());
                if (account == null) continue;

                switch (entry.eventType()) {
                    case "SALE_RECEIVABLE" -> lines.add(debitLine(account.id(), event.total(),
                            "Cliente venta " + event.invoiceNumber()));
                    case "SALE_INCOME" -> lines.add(creditLine(account.id(), event.subtotal(),
                            "Ingreso venta " + event.invoiceNumber()));
                    case "SALE_COGS", "SALE_INVENTORY_OUT" -> {
                        // Handled per-item below
                    }
                }
            }

            // Process SALE_TAX entries — one per rate, only for non-zero amounts
            for (var taxEntry : taxEntries) {
                var account = resolveById(taxEntry.accountId(), taxEntry.eventType());
                if (account == null) continue;

                BigDecimal rateAmount = resolveTaxAmount(event, account);
                if (rateAmount.compareTo(BigDecimal.ZERO) > 0) {
                    int ratePercent = ratePercentFromCode(account.code());
                    lines.add(creditLine(account.id(), rateAmount,
                            "IVA " + ratePercent + "% venta " + event.invoiceNumber()));
                }
            }

            // 2. Per-item entries (COGS + inventory out) — iterate sale items.
            //    The cost of goods sold MUST come from the Kardex (quantity × unitCost of
            //    this sale's EXIT movements), never from the sale price. The POS sale
            //    movements are keyed by the source ORDER id, which the invoice carries in
            //    sourceDocumentId.
            if (doc.items() != null) {
                var costByProduct = resolveCostByProduct(event, doc);
                for (var item : doc.items()) {
                    var productTemplate = templateResolver.resolveForProduct(item.productId(), "SALE");
                    var effectiveEntries = productTemplate.isPresent()
                            ? productTemplate.get().entries()
                            : defaultTemplate.get().entries();

                    for (var entry : effectiveEntries) {
                        if (!"SALE_COGS".equals(entry.eventType())
                                && !"SALE_INVENTORY_OUT".equals(entry.eventType())) {
                            continue;
                        }
                        BigDecimal amount = costByProduct.get(item.productId());
                        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                            log.warn("No Kardex cost found for product {} on sale {}; "
                                            + "skipping {} entry",
                                    item.productId(), event.invoiceNumber(), entry.eventType());
                            continue;
                        }

                        var account = resolveById(entry.accountId(), entry.eventType());
                        if (account == null) continue;

                        String desc = entry.eventType() + " producto " + event.invoiceNumber();
                        if (entry.isDebit()) {
                            lines.add(debitLine(account.id(), amount, desc));
                        } else {
                            lines.add(creditLine(account.id(), amount, desc));
                        }
                    }
                }
            }
        } else {
            // ── FALLBACK: hardcoded PUC accounts (backward compatibility) ──
            var clientAcct = resolveByCode("1305", "SALE receivable");
            var revenueAcct = resolveByCode("4135", "SALE income");
            if (clientAcct == null || revenueAcct == null) {
                log.warn("Missing PUC receivable/income account for sale {}; skipping SALE journal entry",
                        event.invoiceNumber());
                return;
            }

            lines.add(debitLine(clientAcct.id(), event.total(),
                    "Venta " + event.invoiceNumber()));
            lines.add(creditLine(revenueAcct.id(), event.subtotal(),
                    "Ingreso venta " + event.invoiceNumber()));

            // Per-rate tax to sub-accounts (fallback without template)
            Map.of(
                    5, "240805",
                    19, "240815"
            ).forEach((rate, code) -> {
                var amount = switch (rate) {
                    case 5 -> event.taxAmount5();
                    case 19 -> event.taxAmount19();
                    default -> BigDecimal.ZERO;
                };
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    var acct = resolveByCode(code, "SALE tax " + rate + "%");
                    if (acct != null) {
                        lines.add(creditLine(acct.id(), amount,
                                "IVA " + rate + "% venta " + event.invoiceNumber()));
                    }
                }
            });
        }

        postEntryIfBalanced("SALE", event.salesDocumentId(),
                "Factura de venta " + event.invoiceNumber(), lines);
    }

    // ════════════════════════════════════════════════════════════════
    // NOTA CRÉDITO (reversa de la venta)
    // ════════════════════════════════════════════════════════════════

    /**
     * Posts the reversal entry for a credit note (POS devolution).
     *
     * <p>A credit note carries negative totals. Accounting it like a sale would produce negative
     * journal lines and violate the {@code journal_entry_lines} CHECK ({@code debit >= 0},
     * {@code credit >= 0}), aborting the whole devolution. Instead the reversal uses absolute
     * magnitudes and crosses the nature of every sale line:</p>
     * <ul>
     *   <li>{@code SALE_RECEIVABLE} → credited {@code |total|} (undoes the sale debit),</li>
     *   <li>{@code SALE_INCOME} → debited {@code |subtotal|},</li>
     *   <li>each {@code SALE_TAX} → debited {@code |tax_rate|},</li>
     *   <li>{@code SALE_COGS} / {@code SALE_INVENTORY_OUT} → swapped at the returned Kardex cost.</li>
     * </ul>
     */
    private void postCreditNoteReversal(InvoiceIssuedEvent event) {
        var lines = new ArrayList<JournalEntryLine>();

        // Keep the same template resolution as the sale (default → product-specific).
        var defaultTemplate = templateResolver.resolveDefault("SALE");

        if (defaultTemplate.isPresent()) {
            // 1. Aggregate entries (receivable, income) with the sale's nature crossed.
            var taxEntries = new ArrayList<AccountingTemplateEntry>();
            for (var entry : defaultTemplate.get().entries()) {
                if ("SALE_TAX".equals(entry.eventType())) {
                    taxEntries.add(entry);
                    continue;
                }
                var account = resolveById(entry.accountId(), entry.eventType());
                if (account == null) continue;

                switch (entry.eventType()) {
                    case "SALE_RECEIVABLE" -> lines.add(creditLine(account.id(), event.total().abs(),
                            "Reversa cartera NC " + event.invoiceNumber()));
                    case "SALE_INCOME" -> lines.add(debitLine(account.id(), event.subtotal().abs(),
                            "Reversa ingreso NC " + event.invoiceNumber()));
                    case "SALE_COGS", "SALE_INVENTORY_OUT" -> {
                        // Handled per-product below
                    }
                }
            }

            // Per-rate tax reversal — debited (the sale credited it), only for non-zero amounts.
            for (var taxEntry : taxEntries) {
                var account = resolveById(taxEntry.accountId(), taxEntry.eventType());
                if (account == null) continue;

                BigDecimal rateAmount = resolveTaxAmount(event, account).abs();
                if (rateAmount.compareTo(BigDecimal.ZERO) > 0) {
                    int ratePercent = ratePercentFromCode(account.code());
                    lines.add(debitLine(account.id(), rateAmount,
                            "Reversa IVA " + ratePercent + "% NC " + event.invoiceNumber()));
                }
            }

            // 2. Per-product cost reversal — the returned goods re-enter inventory, so the cost
            //    pair is swapped (SALE_COGS credited, SALE_INVENTORY_OUT debited) at the real
            //    returned cost from the Kardex. The devolution records those ENTRY movements
            //    under "SALE" keyed by the credit note id.
            var costByProduct = resolveReturnCostByProduct(event);
            for (var costEntry : costByProduct.entrySet()) {
                UUID productId = costEntry.getKey();
                BigDecimal amount = costEntry.getValue();
                if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                    log.warn("No Kardex return cost found for product {} on credit note {}; "
                                    + "skipping cost reversal",
                            productId, event.invoiceNumber());
                    continue;
                }

                var productTemplate = templateResolver.resolveForProduct(productId, "SALE");
                var effectiveEntries = productTemplate.isPresent()
                        ? productTemplate.get().entries()
                        : defaultTemplate.get().entries();

                for (var entry : effectiveEntries) {
                    if (!"SALE_COGS".equals(entry.eventType())
                            && !"SALE_INVENTORY_OUT".equals(entry.eventType())) {
                        continue;
                    }
                    var account = pucRepo.findById(entry.accountId()).orElse(null);
                    if (account == null) continue;

                    String desc = entry.eventType() + " reversa producto " + event.invoiceNumber();
                    // Cross the nature the template declared for the sale.
                    if (entry.isDebit()) {
                        lines.add(creditLine(account.id(), amount, desc));
                    } else {
                        lines.add(debitLine(account.id(), amount, desc));
                    }
                }
            }
        } else {
            // ── FALLBACK: hardcoded PUC accounts (backward compatibility) ──
            var clientAcct = resolveByCode("1305", "NC receivable");
            var revenueAcct = resolveByCode("4135", "NC income");
            if (clientAcct == null || revenueAcct == null) {
                log.warn("Missing PUC receivable/income account for credit note {}; "
                        + "skipping reversal journal entry", event.invoiceNumber());
                return;
            }

            lines.add(creditLine(clientAcct.id(), event.total().abs(),
                    "Reversa NC " + event.invoiceNumber()));
            lines.add(debitLine(revenueAcct.id(), event.subtotal().abs(),
                    "Reversa ingreso NC " + event.invoiceNumber()));

            Map.of(
                    5, "240805",
                    19, "240815"
            ).forEach((rate, code) -> {
                var amount = switch (rate) {
                    case 5 -> event.taxAmount5().abs();
                    case 19 -> event.taxAmount19().abs();
                    default -> BigDecimal.ZERO;
                };
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    var acct = resolveByCode(code, "NC tax " + rate + "%");
                    if (acct != null) {
                        lines.add(debitLine(acct.id(), amount,
                                "Reversa IVA " + rate + "% NC " + event.invoiceNumber()));
                    }
                }
            });
        }

        postEntryIfBalanced("SALE", event.salesDocumentId(),
                "Nota crédito " + event.invoiceNumber(), lines);
    }

    /**
     * Values the cost returned by a credit note from the Kardex: sums {@code quantity × unitCost}
     * of the note's ENTRY movements (the goods physically re-entering inventory), grouped by
     * product. The devolution records those movements under {@code "SALE"} keyed by the credit
     * note id.
     */
    private Map<UUID, BigDecimal> resolveReturnCostByProduct(InvoiceIssuedEvent event) {
        var costByProduct = new HashMap<UUID, BigDecimal>();
        List<InventoryMovement> movements = kardexRepo.findByReference("SALE", event.salesDocumentId());
        for (InventoryMovement movement : movements) {
            if (movement.movementType() != MovementType.ENTRY) {
                continue;
            }
            BigDecimal quantity = movement.quantity() != null ? movement.quantity() : BigDecimal.ZERO;
            BigDecimal unitCost = movement.unitCost() != null ? movement.unitCost() : BigDecimal.ZERO;
            costByProduct.merge(movement.productId(), quantity.multiply(unitCost).abs(), BigDecimal::add);
        }
        return costByProduct;
    }

    // ════════════════════════════════════════════════════════════════
    // PAGO RECIBIDO (unchanged — out of scope)
    // ════════════════════════════════════════════════════════════════

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCashReceipt(co.posinvent.domain.model.CashReceiptEvent event) {
        try {
            var cashAcct = resolveByCode("1105", "PAYMENT cash");
            var clientAcct = resolveByCode("1305", "PAYMENT receivable");
            if (cashAcct == null || clientAcct == null) {
                log.warn("Missing PUC account for cash receipt {}; skipping PAYMENT journal entry",
                        event.receiptNumber());
                return;
            }

            var lines = new ArrayList<JournalEntryLine>();
            lines.add(debitLine(cashAcct.id(), event.amount(),
                    "Recibo " + event.receiptNumber()));
            lines.add(creditLine(clientAcct.id(), event.amount(),
                    "Abono cliente recibo " + event.receiptNumber()));
            postEntryIfBalanced("PAYMENT", event.receiptId(),
                    "Recibo de caja " + event.receiptNumber(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for cash receipt {}; the business operation is unaffected",
                    event.receiptNumber(), ex);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // AJUSTE DE INVENTARIO (unchanged — out of scope)
    // ════════════════════════════════════════════════════════════════

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAdjustment(co.posinvent.domain.model.AdjustmentAppliedEvent event) {
        try {
            var inventoryAcct = resolveByCode("1435", "INVENTORY inventory");
            var lossAcct = resolveByCode("5195", "INVENTORY loss");
            if (inventoryAcct == null || lossAcct == null) {
                log.warn("Missing PUC account for inventory adjustment '{}'; skipping INVENTORY journal entry",
                        event.reason());
                return;
            }

            var lines = new ArrayList<JournalEntryLine>();
            if (event.delta().compareTo(BigDecimal.ZERO) < 0) {
                var absDelta = event.delta().abs();
                lines.add(debitLine(lossAcct.id(), absDelta,
                        "Pérdida inventario " + event.reason()));
                lines.add(creditLine(inventoryAcct.id(), absDelta,
                        "Ajuste inventario " + event.reason()));
            } else {
                lines.add(debitLine(inventoryAcct.id(), event.delta(),
                        "Sobrante inventario " + event.reason()));
                lines.add(creditLine(lossAcct.id(), event.delta(),
                        "Ingreso sobrante " + event.reason()));
            }
            postEntryIfBalanced("INVENTORY", event.adjustmentId(),
                    "Ajuste inventario: " + event.reason(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for inventory adjustment '{}'; the business operation is unaffected",
                    event.reason(), ex);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // COMPRA
    // ════════════════════════════════════════════════════════════════

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPurchase(co.posinvent.domain.model.PurchaseAccountedEvent event) {
        try {
            postPurchaseEntry(event);
        } catch (Exception ex) {
            log.error("Accounting failed for purchase {}; the business operation is unaffected",
                    event.invoiceNumber(), ex);
        }
    }

    private void postPurchaseEntry(co.posinvent.domain.model.PurchaseAccountedEvent event) {
        // Guard: skip if auto journal generation is disabled
        if (!isJournalGenerationEnabled()) {
            return;
        }

        var lines = new ArrayList<JournalEntryLine>();

        var defaultTemplate = templateResolver.resolveDefault("PURCHASE");

        if (defaultTemplate.isPresent()) {
            for (var entry : defaultTemplate.get().entries()) {
                var account = resolveById(entry.accountId(), entry.eventType());
                if (account == null) continue;

                switch (entry.eventType()) {
                    case "PURCHASE_INVENTORY" -> lines.add(debitLine(account.id(), event.subtotal(),
                            "Compra factura " + event.invoiceNumber()));
                    case "PURCHASE_PAYABLE" -> lines.add(creditLine(account.id(), event.netPayable(),
                            "Proveedor factura " + event.invoiceNumber()));
                    case "PURCHASE_RETENTION" -> {
                        if (event.retefuente().compareTo(BigDecimal.ZERO) > 0) {
                            lines.add(creditLine(account.id(), event.retefuente(),
                                    "Retefuente " + event.invoiceNumber()));
                        }
                    }
                    case "PURCHASE_TAX" -> {
                        if (event.ivaTotal() != null && event.ivaTotal().compareTo(BigDecimal.ZERO) > 0) {
                            lines.add(debitLine(account.id(), event.ivaTotal(),
                                    "IVA descontable " + event.invoiceNumber()));
                        }
                    }
                }
            }

            // Handle ICA retention if present (may use PURCHASE_RETENTION_ICA or just PURCHASE_RETENTION)
            if (event.ica().compareTo(BigDecimal.ZERO) > 0) {
                var icaEntry = defaultTemplate.get().entries().stream()
                        .filter(e -> "PURCHASE_RETENTION_ICA".equals(e.eventType()))
                        .findFirst();
                if (icaEntry.isPresent()) {
                    var account = pucRepo.findById(icaEntry.get().accountId()).orElse(null);
                    if (account != null) {
                        lines.add(creditLine(account.id(), event.ica(),
                                "ICA " + event.invoiceNumber()));
                    }
                }
            }
        } else {
            // ── FALLBACK: hardcoded PUC accounts (backward compatibility) ──
            var inventoryAcct = resolveByCode("1435", "PURCHASE inventory");
            var supplierAcct = resolveByCode("2205", "PURCHASE payable");
            if (inventoryAcct == null || supplierAcct == null) {
                log.warn("Missing PUC account for purchase {}; skipping PURCHASE journal entry",
                        event.invoiceNumber());
                return;
            }

            lines.add(debitLine(inventoryAcct.id(), event.subtotal(),
                    "Compra factura " + event.invoiceNumber()));
            lines.add(creditLine(supplierAcct.id(), event.netPayable(),
                    "Proveedor factura " + event.invoiceNumber()));

            var withholdingFuente = pucRepo.findByCode("2365");
            var withholdingIca = pucRepo.findByCode("2368");
            if (event.retefuente().compareTo(BigDecimal.ZERO) > 0 && withholdingFuente.isPresent()) {
                lines.add(creditLine(withholdingFuente.get().id(), event.retefuente(),
                        "Retefuente " + event.invoiceNumber()));
            }
            if (event.ica().compareTo(BigDecimal.ZERO) > 0 && withholdingIca.isPresent()) {
                lines.add(creditLine(withholdingIca.get().id(), event.ica(),
                        "ICA " + event.invoiceNumber()));
            }
        }

        postEntryIfBalanced("PURCHASE", event.invoiceId(),
                "Factura proveedor " + event.invoiceNumber(), lines);
    }

    // ════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════

    /**
     * Values the inventory cost of a sale from the Kardex: sums {@code quantity × unitCost}
     * of the sale's EXIT movements, grouped by product. The movements are keyed by the
     * source order id, which the invoice carries in {@code sourceDocumentId}.
     */
    private Map<UUID, BigDecimal> resolveCostByProduct(InvoiceIssuedEvent event, SalesDocument doc) {
        UUID saleReferenceId = doc.sourceDocumentId() != null
                ? doc.sourceDocumentId()
                : event.salesDocumentId();

        var costByProduct = new HashMap<UUID, BigDecimal>();
        List<InventoryMovement> movements = kardexRepo.findByReference("SALE", saleReferenceId);
        for (InventoryMovement movement : movements) {
            if (movement.movementType() != MovementType.EXIT) {
                continue;
            }
            BigDecimal quantity = movement.quantity() != null ? movement.quantity() : BigDecimal.ZERO;
            BigDecimal unitCost = movement.unitCost() != null ? movement.unitCost() : BigDecimal.ZERO;
            costByProduct.merge(movement.productId(), quantity.multiply(unitCost), BigDecimal::add);
        }
        return costByProduct;
    }

    private boolean isJournalGenerationEnabled() {
        return companyConfigRepo.findConfig()
                .map(config -> config.autoGenerateJournalEntries())
                .orElse(true); // default to enabled if config not found
    }

    /**
     * Resolves a PUC account by id, warning (instead of failing) when it is absent so the caller
     * can simply omit the affected journal line. Accounting is best-effort after commit.
     */
    private PucAccount resolveById(java.util.UUID accountId, String context) {
        return pucRepo.findById(accountId).orElseGet(() -> {
            log.warn("PUC account {} referenced by {} not found; skipping that journal line",
                    accountId, context);
            return null;
        });
    }

    /** Resolves a PUC account by code, warning instead of throwing when it is absent. */
    private PucAccount resolveByCode(String code, String context) {
        return pucRepo.findByCode(code).orElseGet(() -> {
            log.warn("PUC account with code {} for {} not found; skipping that journal line",
                    code, context);
            return null;
        });
    }

    /**
     * Posts an automatic entry only when it is well formed (non-empty and balanced). A missing
     * PUC account can leave the lines unbalanced; rather than throwing and marking the post-commit
     * transaction rollback-only, the entry is skipped with a warning.
     */
    private void postEntryIfBalanced(String sourceType, java.util.UUID sourceId,
                                     String description, List<JournalEntryLine> lines) {
        if (lines.isEmpty()) {
            log.warn("No journal lines resolved for {} {}; skipping entry", sourceType, sourceId);
            return;
        }
        BigDecimal debit = lines.stream().map(JournalEntryLine::debit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = lines.stream().map(JournalEntryLine::credit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debit.compareTo(credit) != 0) {
            log.warn("Unbalanced journal entry for {} {} (debit={}, credit={}); skipping entry",
                    sourceType, sourceId, debit, credit);
            return;
        }
        journalUseCase.createAuto(sourceType, sourceId, LocalDate.now(), description, lines);
    }

    private JournalEntryLine debitLine(java.util.UUID accountId, BigDecimal amount, String description) {
        return new JournalEntryLine(null, null, accountId, amount, BigDecimal.ZERO, description);
    }

    private JournalEntryLine creditLine(java.util.UUID accountId, BigDecimal amount, String description) {
        return new JournalEntryLine(null, null, accountId, BigDecimal.ZERO, amount, description);
    }

    /**
     * Resolves the tax amount for a given PUC account by matching its code suffix
     * to the corresponding per-rate field on the event.
     */
    private BigDecimal resolveTaxAmount(InvoiceIssuedEvent event, PucAccount account) {
        return switch (account.code()) {
            case "240805" -> event.taxAmount5();
            case "240815" -> event.taxAmount19();
            default -> event.taxAmount0();
        };
    }

    /**
     * Extracts the tax rate percentage from a PUC account code suffix.
     * 240805 → 5, 240815 → 19, fallback → 0.
     */
    private int ratePercentFromCode(String code) {
        return switch (code) {
            case "240805" -> 5;
            case "240815" -> 19;
            default -> 0;
        };
    }
}
