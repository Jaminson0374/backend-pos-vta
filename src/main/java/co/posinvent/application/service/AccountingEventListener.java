package co.posinvent.application.service;

import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.AccountingTemplateEntry;
import co.posinvent.domain.model.InvoiceIssuedEvent;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@Service
public class AccountingEventListener {

    private final CreateJournalEntryUseCase journalUseCase;
    private final PucAccountRepository pucRepo;
    private final TemplateResolverService templateResolver;
    private final CompanyConfigRepository companyConfigRepo;
    private final SalesDocumentRepository salesDocumentRepo;

    public AccountingEventListener(
            CreateJournalEntryUseCase journalUseCase,
            PucAccountRepository pucRepo,
            TemplateResolverService templateResolver,
            CompanyConfigRepository companyConfigRepo,
            SalesDocumentRepository salesDocumentRepo) {
        this.journalUseCase = journalUseCase;
        this.pucRepo = pucRepo;
        this.templateResolver = templateResolver;
        this.companyConfigRepo = companyConfigRepo;
        this.salesDocumentRepo = salesDocumentRepo;
    }

    // ════════════════════════════════════════════════════════════════
    // VENTA
    // ════════════════════════════════════════════════════════════════

    @EventListener
    public void onInvoiceIssued(InvoiceIssuedEvent event) {
        // Guard: skip if auto journal generation is disabled
        if (!isJournalGenerationEnabled()) {
            return;
        }

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
                var account = pucRepo.findById(entry.accountId()).orElse(null);
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
                var account = pucRepo.findById(taxEntry.accountId()).orElse(null);
                if (account == null) continue;

                BigDecimal rateAmount = resolveTaxAmount(event, account);
                if (rateAmount.compareTo(BigDecimal.ZERO) > 0) {
                    int ratePercent = ratePercentFromCode(account.code());
                    lines.add(creditLine(account.id(), rateAmount,
                            "IVA " + ratePercent + "% venta " + event.invoiceNumber()));
                }
            }

            // 2. Per-item entries (COGS + inventory out) — iterate sale items
            var doc = salesDocumentRepo.findByIdWithItems(event.salesDocumentId());
            if (doc.isPresent() && doc.get().items() != null) {
                for (var item : doc.get().items()) {
                    var productTemplate = templateResolver.resolveForProduct(item.productId(), "SALE");
                    var effectiveEntries = productTemplate.isPresent()
                            ? productTemplate.get().entries()
                            : defaultTemplate.get().entries();

                    for (var entry : effectiveEntries) {
                        if (!"SALE_COGS".equals(entry.eventType())
                                && !"SALE_INVENTORY_OUT".equals(entry.eventType())) {
                            continue;
                        }
                        BigDecimal amount = item.subtotal();
                        if (amount.compareTo(BigDecimal.ZERO) == 0) continue;

                        var account = pucRepo.findById(entry.accountId()).orElse(null);
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
            var clientAcct = pucRepo.findByCode("1305").orElseThrow();
            var revenueAcct = pucRepo.findByCode("4135").orElseThrow();

            lines.add(debitLine(clientAcct.id(), event.total(),
                    "Venta " + event.invoiceNumber()));
            lines.add(creditLine(revenueAcct.id(), event.subtotal(),
                    "Ingreso venta " + event.invoiceNumber()));

            // Per-rate tax to sub-accounts (fallback without template)
            Map.of(
                    5, "240805",
                    8, "240810",
                    19, "240815"
            ).forEach((rate, code) -> {
                var amount = switch (rate) {
                    case 5 -> event.taxAmount5();
                    case 8 -> event.taxAmount8();
                    case 19 -> event.taxAmount19();
                    default -> BigDecimal.ZERO;
                };
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    var acct = pucRepo.findByCode(code).orElseThrow();
                    lines.add(creditLine(acct.id(), amount,
                            "IVA " + rate + "% venta " + event.invoiceNumber()));
                }
            });
        }

        journalUseCase.createAuto("SALE", event.salesDocumentId(), LocalDate.now(),
                "Factura de venta " + event.invoiceNumber(), lines);
    }

    // ════════════════════════════════════════════════════════════════
    // PAGO RECIBIDO (unchanged — out of scope)
    // ════════════════════════════════════════════════════════════════

    @EventListener
    public void onCashReceipt(co.posinvent.domain.model.CashReceiptEvent event) {
        var cashAcct = pucRepo.findByCode("1105").orElseThrow();
        var clientAcct = pucRepo.findByCode("1305").orElseThrow();

        var lines = new ArrayList<JournalEntryLine>();
        lines.add(debitLine(cashAcct.id(), event.amount(),
                "Recibo " + event.receiptNumber()));
        lines.add(creditLine(clientAcct.id(), event.amount(),
                "Abono cliente recibo " + event.receiptNumber()));
        journalUseCase.createAuto("PAYMENT", event.receiptId(), LocalDate.now(),
                "Recibo de caja " + event.receiptNumber(), lines);
    }

    // ════════════════════════════════════════════════════════════════
    // AJUSTE DE INVENTARIO (unchanged — out of scope)
    // ════════════════════════════════════════════════════════════════

    @EventListener
    public void onAdjustment(co.posinvent.domain.model.AdjustmentAppliedEvent event) {
        var inventoryAcct = pucRepo.findByCode("1435").orElseThrow();
        var lossAcct = pucRepo.findByCode("5195").orElseThrow();

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
        journalUseCase.createAuto("INVENTORY", event.adjustmentId(), LocalDate.now(),
                "Ajuste inventario: " + event.reason(), lines);
    }

    // ════════════════════════════════════════════════════════════════
    // COMPRA
    // ════════════════════════════════════════════════════════════════

    @EventListener
    public void onPurchase(co.posinvent.domain.model.PurchaseAccountedEvent event) {
        // Guard: skip if auto journal generation is disabled
        if (!isJournalGenerationEnabled()) {
            return;
        }

        var lines = new ArrayList<JournalEntryLine>();

        var defaultTemplate = templateResolver.resolveDefault("PURCHASE");

        if (defaultTemplate.isPresent()) {
            for (var entry : defaultTemplate.get().entries()) {
                var account = pucRepo.findById(entry.accountId()).orElse(null);
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
                        // Tax on purchase — not in legacy but available via template
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
            var inventoryAcct = pucRepo.findByCode("1435").orElseThrow();
            var supplierAcct = pucRepo.findByCode("2205").orElseThrow();

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

        journalUseCase.createAuto("PURCHASE", event.invoiceId(), LocalDate.now(),
                "Factura proveedor " + event.invoiceNumber(), lines);
    }

    // ════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════

    private boolean isJournalGenerationEnabled() {
        return companyConfigRepo.findConfig()
                .map(config -> config.autoGenerateJournalEntries())
                .orElse(true); // default to enabled if config not found
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
            case "240810" -> event.taxAmount8();
            case "240815" -> event.taxAmount19();
            default -> event.taxAmount0();
        };
    }

    /**
     * Extracts the tax rate percentage from a PUC account code suffix.
     * 240805 → 5, 240810 → 8, 240815 → 19, fallback → 0.
     */
    private int ratePercentFromCode(String code) {
        return switch (code) {
            case "240805" -> 5;
            case "240810" -> 8;
            case "240815" -> 19;
            default -> 0;
        };
    }
}
