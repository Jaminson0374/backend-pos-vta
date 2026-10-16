package co.posinvent.application.service;

import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.AccountingTemplateEntry;
import co.posinvent.domain.model.AdjustmentAppliedEvent;
import co.posinvent.domain.model.DisposalAccountedEvent;
import co.posinvent.domain.model.GoodsReceiptAccountedEvent;
import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.InvoiceIssuedEvent;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.ProductionAccountedEvent;
import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.ShiftClosedAccountedEvent;
import co.posinvent.domain.model.SupplierNoteAccountedEvent;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountingEventListener {

    private static final Logger log = LoggerFactory.getLogger(AccountingEventListener.class);

    /** Mercancía recibida no facturada — puente transitorio entre recepción y factura (WU-36b). */
    private static final String BRIDGE_ACCOUNT_CODE = "220510";
    /** Inventario de mercancías — reconocido una sola vez, en la recepción (WU-36b). */
    private static final String INVENTORY_ACCOUNT_CODE = "1435";
    /** Proveedores nacionales. */
    private static final String PAYABLE_ACCOUNT_CODE = "2205";
    /** Pérdida por inventario (decomisos, faltantes, ajustes negativos). */
    private static final String LOSS_ACCOUNT_CODE = "5195";
    /**
     * Variación/costo de producción: absorbs the difference between the finished-goods cost and
     * the consumed-components cost (the conversion cost added by the production). Seeded by V126.
     */
    private static final String PRODUCTION_VARIANCE_ACCOUNT_CODE = "710595";

    /** Gasto por faltante de caja al cerrar un turno con arqueo negativo (WU-36d). */
    private static final String CASH_SHORTAGE_ACCOUNT_CODE = "529520";
    /** Ingreso por sobrante de caja al cerrar un turno con arqueo positivo (WU-36d). */
    private static final String CASH_SURPLUS_ACCOUNT_CODE = "429515";
    /** Caja. */
    private static final String CASH_ACCOUNT_CODE = "1105";

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
                case CREDIT_NOTE -> postCreditNoteReversal(event, doc);
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

        postEntryIfBalanced("SALE", event.salesDocumentId(), documentDate(doc.createdAt()),
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
    private void postCreditNoteReversal(InvoiceIssuedEvent event, SalesDocument doc) {
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

        postEntryIfBalanced("SALE", event.salesDocumentId(), documentDate(doc.createdAt()),
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
            postEntryIfBalanced("PAYMENT", event.receiptId(), entryDate(event.entryDate()),
                    "Recibo de caja " + event.receiptNumber(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for cash receipt {}; the business operation is unaffected",
                    event.receiptNumber(), ex);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // AJUSTE DE INVENTARIO (WU-36c: publisher added)
    // ════════════════════════════════════════════════════════════════

    /**
     * Posts the accounting for a committed inventory adjustment (WU-36c). A negative delta is a
     * loss (debit pérdida {@code 5195} / credit inventario {@code 1435}); a positive delta is a
     * surplus (debit inventario / credit pérdida). Always posted with positive magnitudes and
     * dated with the adjustment itself.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAdjustment(AdjustmentAppliedEvent event) {
        try {
            if (event.delta() == null || event.delta().compareTo(BigDecimal.ZERO) == 0) {
                log.warn("Inventory adjustment '{}' has a zero delta; skipping INVENTORY journal entry",
                        event.reason());
                return;
            }

            var inventoryAcct = resolveByCode(INVENTORY_ACCOUNT_CODE, "INVENTORY inventory");
            var lossAcct = resolveByCode(LOSS_ACCOUNT_CODE, "INVENTORY loss");
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
            postEntryIfBalanced("INVENTORY", event.adjustmentId(), entryDate(event.entryDate()),
                    "Ajuste inventario: " + event.reason(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for inventory adjustment '{}'; the business operation is unaffected",
                    event.reason(), ex);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // DECOMISO / DISPOSICIÓN DE INVENTARIO (WU-36c)
    // ════════════════════════════════════════════════════════════════

    /**
     * Posts the accounting for a committed disposal/waste (WU-36c): the disposed inventory is
     * written off at its Kardex/stock cost — debit pérdida {@code 5195} / credit inventario
     * {@code 1435} — dated with the disposal. Skipped with a warning when the disposal carries no
     * positive cost.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDisposal(DisposalAccountedEvent event) {
        try {
            if (!isJournalGenerationEnabled()) {
                return;
            }
            var totalCost = event.totalCost() != null ? event.totalCost() : BigDecimal.ZERO;
            if (totalCost.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Disposal {} has no inventory cost; skipping INVENTORY journal entry",
                        event.disposalNumber());
                return;
            }

            var lossAcct = resolveByCode(LOSS_ACCOUNT_CODE, "INVENTORY loss");
            var inventoryAcct = resolveByCode(INVENTORY_ACCOUNT_CODE, "INVENTORY inventory");
            if (lossAcct == null || inventoryAcct == null) {
                log.warn("Missing PUC account for disposal {}; skipping INVENTORY journal entry",
                        event.disposalNumber());
                return;
            }

            var lines = new ArrayList<JournalEntryLine>();
            lines.add(debitLine(lossAcct.id(), totalCost,
                    "Decomiso inventario " + event.disposalNumber()));
            lines.add(creditLine(inventoryAcct.id(), totalCost,
                    "Salida inventario decomiso " + event.disposalNumber()));
            postEntryIfBalanced("INVENTORY", event.disposalId(), entryDate(event.entryDate()),
                    "Decomiso de inventario " + event.disposalNumber(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for disposal {}; the business operation is unaffected",
                    event.disposalNumber(), ex);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // PRODUCCIÓN (WU-36c)
    // ════════════════════════════════════════════════════════════════

    /**
     * Posts the accounting for a committed production (WU-36c): the finished goods enter inventory
     * (debit {@code 1435}) at {@code outputCost} and the consumed components leave it (credit
     * {@code 1435}) at {@code inputCost}. The difference (the conversion cost) is posted to the
     * production variance/cost account. When no such account is configured the entry is skipped
     * with a warning (documented limitation — see {@link #PRODUCTION_VARIANCE_ACCOUNT_CODE}).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProduction(ProductionAccountedEvent event) {
        try {
            if (!isJournalGenerationEnabled()) {
                return;
            }
            var inputCost = event.inputCost() != null ? event.inputCost() : BigDecimal.ZERO;
            var outputCost = event.outputCost() != null ? event.outputCost() : BigDecimal.ZERO;
            if (inputCost.compareTo(BigDecimal.ZERO) <= 0 && outputCost.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Production {} has no cost; skipping PRODUCTION journal entry",
                        event.productionNumber());
                return;
            }

            var inventoryAcct = resolveByCode(INVENTORY_ACCOUNT_CODE, "PRODUCTION inventory");
            if (inventoryAcct == null) {
                log.warn("Missing PUC inventory account for production {}; skipping PRODUCTION journal entry",
                        event.productionNumber());
                return;
            }

            var difference = outputCost.subtract(inputCost);
            PucAccount varianceAcct = null;
            if (difference.compareTo(BigDecimal.ZERO) != 0) {
                varianceAcct = resolveByCode(PRODUCTION_VARIANCE_ACCOUNT_CODE, "PRODUCTION variance");
                if (varianceAcct == null) {
                    log.warn("No production variance/cost account ({}) found; skipping PRODUCTION journal entry {}",
                            PRODUCTION_VARIANCE_ACCOUNT_CODE, event.productionNumber());
                    return;
                }
            }

            var lines = new ArrayList<JournalEntryLine>();
            if (outputCost.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(debitLine(inventoryAcct.id(), outputCost,
                        "Producto terminado producción " + event.productionNumber()));
            }
            if (inputCost.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(creditLine(inventoryAcct.id(), inputCost,
                        "Consumo componentes producción " + event.productionNumber()));
            }
            if (varianceAcct != null) {
                if (difference.compareTo(BigDecimal.ZERO) > 0) {
                    lines.add(creditLine(varianceAcct.id(), difference,
                            "Variación costos producción " + event.productionNumber()));
                } else {
                    lines.add(debitLine(varianceAcct.id(), difference.abs(),
                            "Variación costos producción " + event.productionNumber()));
                }
            }
            postEntryIfBalanced("PRODUCTION", event.productionId(), entryDate(event.entryDate()),
                    "Producción " + event.productionNumber(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for production {}; the business operation is unaffected",
                    event.productionNumber(), ex);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // CIERRE DE TURNO / ARQUEO (WU-36d)
    // ════════════════════════════════════════════════════════════════

    /**
     * Posts the arqueo adjustment for a closed shift (WU-36d). The event is only published when
     * the cash count leaves a difference ({@code difference != 0}), so a perfect match posts
     * nothing:
     *
     * <ul>
     *   <li>Faltante ({@code difference > 0}, esperado &gt; contado) → debit FALTANTE DE CAJA
     *       {@code 529520} / credit caja {@code 1105} for the absolute difference.</li>
     *   <li>Sobrante ({@code difference < 0}, contado &gt; esperado) → debit caja {@code 1105} /
     *       credit SOBRANTE DE CAJA {@code 429515} for the absolute difference.</li>
     * </ul>
     *
     * <p>Always posted with positive magnitudes, dated with the shift's close date. Best-effort:
     * runs {@code AFTER_COMMIT} in its own transaction and swallows any accounting failure so the
     * already-committed shift close is never rolled back.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShiftClose(ShiftClosedAccountedEvent event) {
        try {
            if (!isJournalGenerationEnabled()) {
                return;
            }

            var difference = event.difference() != null ? event.difference() : BigDecimal.ZERO;
            if (difference.signum() == 0) {
                log.warn("Shift {} closed with no arqueo difference; skipping SHIFT_CLOSE journal entry",
                        event.shiftNumber());
                return;
            }

            var cashAcct = resolveByCode(CASH_ACCOUNT_CODE, "SHIFT_CLOSE cash");
            if (cashAcct == null) {
                log.warn("Missing PUC cash account ({}) for shift {}; skipping SHIFT_CLOSE journal entry",
                        CASH_ACCOUNT_CODE, event.shiftNumber());
                return;
            }

            var magnitude = difference.abs();
            var lines = new ArrayList<JournalEntryLine>();
            if (difference.signum() > 0) {
                // Faltante: esperado > contado → débito faltante / crédito caja.
                var shortageAcct = resolveByCode(CASH_SHORTAGE_ACCOUNT_CODE, "SHIFT_CLOSE shortage");
                if (shortageAcct == null) {
                    log.warn("Missing PUC shortage account ({}) for shift {}; skipping SHIFT_CLOSE journal entry",
                            CASH_SHORTAGE_ACCOUNT_CODE, event.shiftNumber());
                    return;
                }
                lines.add(debitLine(shortageAcct.id(), magnitude,
                        "Faltante de caja turno " + event.shiftNumber()));
                lines.add(creditLine(cashAcct.id(), magnitude,
                        "Ajuste caja faltante turno " + event.shiftNumber()));
            } else {
                // Sobrante: contado > esperado → débito caja / crédito sobrante.
                var surplusAcct = resolveByCode(CASH_SURPLUS_ACCOUNT_CODE, "SHIFT_CLOSE surplus");
                if (surplusAcct == null) {
                    log.warn("Missing PUC surplus account ({}) for shift {}; skipping SHIFT_CLOSE journal entry",
                            CASH_SURPLUS_ACCOUNT_CODE, event.shiftNumber());
                    return;
                }
                lines.add(debitLine(cashAcct.id(), magnitude,
                        "Ajuste caja sobrante turno " + event.shiftNumber()));
                lines.add(creditLine(surplusAcct.id(), magnitude,
                        "Sobrante de caja turno " + event.shiftNumber()));
            }
            postEntryIfBalanced("SHIFT_CLOSE", event.shiftId(), entryDate(event.entryDate()),
                    "Ajuste de arqueo turno " + event.shiftNumber(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for shift {}; the business operation is unaffected",
                    event.shiftNumber(), ex);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // RECEPCIÓN DE MERCANCÍA (C6, WU-36b)
    // ════════════════════════════════════════════════════════════════

    /**
     * Posts the inventory recognition for a committed goods receipt (C6). Per NIIF the
     * inventory is recognised when physical control is assumed, so the receipt debits
     * inventory {@code 1435} and credits the bridge account {@code 220510} ("mercancía
     * recibida no facturada"). The supplier invoice later debits that same bridge, so the
     * inventory is recognised exactly once.
     *
     * <p>Best-effort: runs {@code AFTER_COMMIT} in its own transaction and swallows any
     * accounting failure so the committed receipt is never rolled back.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGoodsReceipt(GoodsReceiptAccountedEvent event) {
        try {
            if (!isJournalGenerationEnabled()) {
                return;
            }
            if (event.totalCost() == null || event.totalCost().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Goods receipt {} has no entry cost; skipping INVENTORY journal entry",
                        event.voucherNumber());
                return;
            }

            var inventoryAcct = resolveByCode(INVENTORY_ACCOUNT_CODE, "INVENTORY inventory");
            var bridgeAcct = resolveByCode(BRIDGE_ACCOUNT_CODE, "INVENTORY bridge");
            if (inventoryAcct == null || bridgeAcct == null) {
                log.warn("Missing PUC account for goods receipt {}; skipping INVENTORY journal entry",
                        event.voucherNumber());
                return;
            }

            var lines = new ArrayList<JournalEntryLine>();
            lines.add(debitLine(inventoryAcct.id(), event.totalCost(),
                    "Recepción mercancía " + event.voucherNumber()));
            lines.add(creditLine(bridgeAcct.id(), event.totalCost(),
                    "Mercancía recibida no facturada " + event.voucherNumber()));
            postEntryIfBalanced("INVENTORY", event.voucherId(), entryDate(event.entryDate()),
                    "Recepción de mercancía " + event.voucherNumber(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for goods receipt {}; the business operation is unaffected",
                    event.voucherNumber(), ex);
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

    /**
     * Posts the supplier invoice entry (C6 → invoice). The invoice no longer debits inventory:
     * the debit goes to the bridge account {@code 220510} to reverse the temporary liability
     * recognised by the receipt, keeping the inventory recognised exactly once.
     *
     * <p><b>Purchase price difference.</b> When the invoice subtotal differs from the receipt
     * cost, that difference stays absorbed in the bridge account and is cleared by a periodic
     * adjustment. {@code SupplierInvoice} only links to purchase orders ({@code ocIds}), not to a
     * specific receipt, and several invoices may reference the same OC: summing all receipts per
     * OC would overstate the difference and could double-clear the bridge, so no difference amount
     * is posted here (the {@code 529515} adjustment account is available for the periodic entry).</p>
     */
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
                    case "PURCHASE_INVENTORY" -> {
                        // WU-36b: the inventory was recognised at the receipt (C6). The invoice
                        // clears the bridge liability instead of debiting inventory a second time.
                        var bridge = resolveByCode(BRIDGE_ACCOUNT_CODE, "PURCHASE bridge");
                        if (bridge != null) {
                            lines.add(debitLine(bridge.id(), event.subtotal(),
                                    "Compra factura " + event.invoiceNumber()));
                        }
                    }
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
            var bridgeAcct = resolveByCode(BRIDGE_ACCOUNT_CODE, "PURCHASE bridge");
            var supplierAcct = resolveByCode(PAYABLE_ACCOUNT_CODE, "PURCHASE payable");
            if (bridgeAcct == null || supplierAcct == null) {
                log.warn("Missing PUC account for purchase {}; skipping PURCHASE journal entry",
                        event.invoiceNumber());
                return;
            }

            lines.add(debitLine(bridgeAcct.id(), event.subtotal(),
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

        postEntryIfBalanced("PURCHASE", event.invoiceId(), entryDate(event.entryDate()),
                "Factura proveedor " + event.invoiceNumber(), lines);
    }

    // ════════════════════════════════════════════════════════════════
    // PAGO A PROVEEDOR
    // ════════════════════════════════════════════════════════════════

    /**
     * Posts the accounting for a supplier payment after its business transaction has committed
     * (WU-34): debit the payable the purchase credited, credit cash. Runs {@code AFTER_COMMIT}
     * in its own {@code REQUIRES_NEW} transaction and swallows any accounting failure so the
     * already-committed payment is never rolled back. Best-effort posting.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSupplierPayment(co.posinvent.domain.model.SupplierPaymentEvent event) {
        try {
            // Guard: skip if auto journal generation is disabled
            if (!isJournalGenerationEnabled()) {
                return;
            }

            var payableAcct = resolvePayableAccount();
            var cashAcct = resolveByCode("1105", "PAYMENT cash");
            if (payableAcct == null || cashAcct == null) {
                log.warn("Missing PUC account for supplier payment {}; skipping PAYMENT journal entry",
                        event.paymentNumber());
                return;
            }

            var lines = new ArrayList<JournalEntryLine>();
            lines.add(debitLine(payableAcct.id(), event.amount(),
                    "Pago proveedor " + event.paymentNumber()));
            lines.add(creditLine(cashAcct.id(), event.amount(),
                    "Salida caja pago " + event.paymentNumber()));
            postEntryIfBalanced("PAYMENT", event.paymentId(), entryDate(event.entryDate()),
                    "Pago a proveedor " + event.paymentNumber(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for supplier payment {}; the business operation is unaffected",
                    event.paymentNumber(), ex);
        }
    }

    /**
     * Resolves the payable account used by the payment, mirroring {@code onPurchase}: the
     * PURCHASE template's {@code PURCHASE_PAYABLE} entry when present, else the hardcoded
     * PUC code {@code 2205}. Returns {@code null} (warn+skip) when neither resolves.
     */
    private PucAccount resolvePayableAccount() {
        var defaultTemplate = templateResolver.resolveDefault("PURCHASE");
        if (defaultTemplate.isPresent()) {
            for (var entry : defaultTemplate.get().entries()) {
                if ("PURCHASE_PAYABLE".equals(entry.eventType())) {
                    var account = resolveById(entry.accountId(), "PAYMENT payable");
                    if (account != null) {
                        return account;
                    }
                }
            }
        }
        return resolveByCode("2205", "PAYMENT payable");
    }

    // ════════════════════════════════════════════════════════════════
    // NOTA DÉBITO / CRÉDITO DE PROVEEDOR (C5, WU-36b)
    // ════════════════════════════════════════════════════════════════

    /**
     * Posts the accounting for a committed supplier debit/credit note (C5). The note adjusts
     * what we owe against the bridge account:
     *
     * <ul>
     *   <li>Credit note (the supplier credits us) → debit proveedores {@code 2205} /
     *       credit puente {@code 220510}.</li>
     *   <li>Debit note (the supplier charges us more) → debit puente {@code 220510} /
     *       credit proveedores {@code 2205}.</li>
     * </ul>
     *
     * <p>Best-effort: {@code AFTER_COMMIT} in its own transaction, dated with the note.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSupplierNote(SupplierNoteAccountedEvent event) {
        try {
            if (!isJournalGenerationEnabled()) {
                return;
            }

            var amount = event.amount() != null ? event.amount().abs() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Supplier note {} has no amount; skipping PURCHASE journal entry",
                        event.noteNumber());
                return;
            }

            var payableAcct = resolveByCode(PAYABLE_ACCOUNT_CODE, "PURCHASE payable");
            var bridgeAcct = resolveByCode(BRIDGE_ACCOUNT_CODE, "PURCHASE bridge");
            if (payableAcct == null || bridgeAcct == null) {
                log.warn("Missing PUC account for supplier note {}; skipping PURCHASE journal entry",
                        event.noteNumber());
                return;
            }

            var lines = new ArrayList<JournalEntryLine>();
            if (event.isDebitNote()) {
                lines.add(debitLine(bridgeAcct.id(), amount,
                        "Nota débito proveedor " + event.noteNumber()));
                lines.add(creditLine(payableAcct.id(), amount,
                        "Nota débito proveedor " + event.noteNumber()));
            } else {
                lines.add(debitLine(payableAcct.id(), amount,
                        "Nota crédito proveedor " + event.noteNumber()));
                lines.add(creditLine(bridgeAcct.id(), amount,
                        "Nota crédito proveedor " + event.noteNumber()));
            }
            postEntryIfBalanced("PURCHASE", event.noteId(), entryDate(event.entryDate()),
                    "Nota proveedor " + event.noteNumber(), lines);
        } catch (Exception ex) {
            log.error("Accounting failed for supplier note {}; the business operation is unaffected",
                    event.noteNumber(), ex);
        }
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
    private void postEntryIfBalanced(String sourceType, java.util.UUID sourceId, LocalDate entryDate,
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
        journalUseCase.createAuto(sourceType, sourceId, entryDate, description, lines);
    }

    /**
     * The accounting date of a document is its creation date (A5): a retroactive invoice is
     * posted inside the period it belongs to, not the period where the event happened to run.
     * Falls back to the current date when the document carries no timestamp.
     */
    private LocalDate documentDate(OffsetDateTime createdAt) {
        return createdAt != null ? createdAt.toLocalDate() : LocalDate.now();
    }

    /**
     * The accounting date carried by a business event (A5). Falls back to the current date when
     * the event has no document date, preserving the previous behaviour for legacy publishers.
     */
    private LocalDate entryDate(LocalDate eventDate) {
        return eventDate != null ? eventDate : LocalDate.now();
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
