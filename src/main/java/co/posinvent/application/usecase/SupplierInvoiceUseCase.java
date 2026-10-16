package co.posinvent.application.usecase;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.dto.SupplierInvoiceResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.PurchaseAccountedEvent;
import co.posinvent.domain.model.PurchaseRetentionConfig;
import co.posinvent.domain.model.SupplierInvoice;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.PurchaseRetentionConfigRepository;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import co.posinvent.domain.repository.ThirdPartyCategoryRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.domain.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SupplierInvoiceUseCase {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Roles allowed to set a manual retention override (backend-owned retention policy, A2). */
    private static final Set<String> OVERRIDE_ROLES = Set.of("ADMIN", "CONTADOR");

    private final SupplierInvoiceRepository invoiceRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final ThirdPartyCategoryRepository thirdPartyCategoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountsPayableUseCase accountsPayableUseCase;
    private final PurchaseRetentionConfigRepository retentionConfigRepository;
    private final CompanyConfigRepository companyConfigRepository;
    private final UserRepository userRepository;

    public SupplierInvoiceUseCase(
            SupplierInvoiceRepository invoiceRepository,
            ThirdPartyRepository thirdPartyRepository,
            ThirdPartyCategoryRepository thirdPartyCategoryRepository,
            ApplicationEventPublisher eventPublisher,
            AccountsPayableUseCase accountsPayableUseCase,
            PurchaseRetentionConfigRepository retentionConfigRepository,
            CompanyConfigRepository companyConfigRepository,
            UserRepository userRepository
    ) {
        this.invoiceRepository = invoiceRepository;
        this.thirdPartyRepository = thirdPartyRepository;
        this.thirdPartyCategoryRepository = thirdPartyCategoryRepository;
        this.eventPublisher = eventPublisher;
        this.accountsPayableUseCase = accountsPayableUseCase;
        this.retentionConfigRepository = retentionConfigRepository;
        this.companyConfigRepository = companyConfigRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierInvoiceResponse> list(Pageable pageable) {
        return PageResponse.from(
                invoiceRepository.findAll(pageable),
                SupplierInvoiceResponse::from
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierInvoiceResponse> findBySupplier(UUID supplierId, Pageable pageable) {
        return PageResponse.from(
                invoiceRepository.findBySupplierId(supplierId, pageable),
                SupplierInvoiceResponse::from
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierInvoiceResponse> findByStatus(InvoiceStatus status, Pageable pageable) {
        return PageResponse.from(
                invoiceRepository.findByStatus(status, pageable),
                SupplierInvoiceResponse::from
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierInvoiceResponse> findBySupplierAndStatus(
            UUID supplierId, InvoiceStatus status, Pageable pageable) {
        return PageResponse.from(
                invoiceRepository.findBySupplierIdAndStatus(supplierId, status, pageable),
                SupplierInvoiceResponse::from
        );
    }

    @Transactional(readOnly = true)
    public SupplierInvoiceResponse getById(UUID id) {
        return invoiceRepository.findById(id)
                .map(SupplierInvoiceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Factura de proveedor", id));
    }

    @Transactional
    public SupplierInvoiceResponse create(SupplierInvoiceRequest request, UUID operatorId) {
        // Validate supplier exists and has correct type
        var supplier = thirdPartyRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.supplierId()));

        if (!isSupplier(supplier)) {
            throw new BusinessException("INV_NOT_A_SUPPLIER",
                    "El tercero seleccionado no es un proveedor. Tipo: " + supplier.type());
        }

        // Data-driven retention (Contabilidad Fase 2, A3/A4): retefuente is selected from the
        // active PurchaseRetentionConfig catalog (matching the supplier, baseMin satisfied) and
        // ICA comes from company_config.purchase_ica_rate. A non-null request.retentionTotal is a
        // manual override of the whole retention; null triggers the data-driven calculation.
        var isOverride = request.retentionTotal() != null;
        if (isOverride && !canOverrideRetention(operatorId)) {
            throw new BusinessException("RETENTION_OVERRIDE_FORBIDDEN",
                    "Solo un administrador o contador puede fijar la retención manualmente");
        }

        var retention = computeRetention(request.subtotal(), supplier);
        var effectiveRetention = isOverride ? request.retentionTotal() : retention.total();
        // The journal entry must stay balanced: retefuente + ica must equal the effective
        // retention. A manual override carries no split, so it is posted entirely as retefuente.
        var retefuente = isOverride ? effectiveRetention : retention.retefuente();
        var ica = isOverride ? BigDecimal.ZERO : retention.ica();

        // The backend owns the total when the retention is automatic: it is recomputed from
        // subtotal + IVA - effectiveRetention (any client-provided total is ignored). A manual
        // override keeps the DIAN consistency validation with a 1.0 tolerance.
        var computedTotal = request.subtotal()
                .add(request.ivaTotal())
                .subtract(effectiveRetention);
        BigDecimal effectiveTotal;
        if (isOverride) {
            var diff = request.total().subtract(computedTotal).abs();
            if (diff.compareTo(BigDecimal.ONE) > 0) {
                throw new BusinessException("INV_DIAN_MISMATCH",
                        "El total no coincide con subtotal + IVA - retención. " +
                        "Total recibido: " + request.total() + ", Calculado: " + computedTotal);
            }
            effectiveTotal = request.total();
        } else {
            effectiveTotal = computedTotal;
        }

        // Check invoice number uniqueness per supplier
        invoiceRepository.findByInvoiceNumberAndSupplierId(
                        request.invoiceNumber(), request.supplierId())
                .ifPresent(existing -> {
                    throw new BusinessException("INV_DUPLICATE_NUMBER",
                            "Ya existe una factura con número " + request.invoiceNumber() +
                            " para este proveedor.");
                });

        var invoice = new SupplierInvoice(
                UUID.randomUUID(),
                request.supplierId(),
                request.invoiceNumber(),
                request.issueDate(),
                request.dueDate(),
                request.subtotal(),
                request.ivaTotal(),
                effectiveRetention,
                effectiveTotal,
                InvoiceStatus.PENDING,
                request.notes(),
                operatorId,
                null,
                null,
                null,
                request.ocIds() != null ? request.ocIds() : List.of()
        );

        var saved = invoiceRepository.save(invoice);

        // Publish PurchaseAccountedEvent for automatic journal entry generation
        eventPublisher.publishEvent(new PurchaseAccountedEvent(
                this,
                saved.id(),
                saved.invoiceNumber(),
                saved.subtotal(),
                saved.ivaTotal(),
                saved.total(),          // netPayable = total
                retefuente,             // retefuente retention (data-driven or override)
                ica,                    // ICA retention (empresa-level rate)
                saved.issueDate()       // accounting date = document date
        ));

        // No static supplier balance write here: the payable side is sourced exclusively from the
        // AccountsPayable opened on reconcile (hallazgo C2 double-book fix). The invoice total is
        // not added to third_parties.current_balance anymore.
        return SupplierInvoiceResponse.from(saved);
    }

    /**
     * The retention override is a restricted action: only an ADMIN or CONTADOR operator may set it.
     * Unknown operators (or a null operator) are treated as unauthorized.
     */
    private boolean canOverrideRetention(UUID operatorId) {
        if (operatorId == null) {
            return false;
        }
        return userRepository.findById(operatorId)
                .map(user -> user.role() != null && OVERRIDE_ROLES.contains(user.role().name()))
                .orElse(false);
    }

    /**
     * Computed retention split for a purchase base (subtotal without IVA).
     */
    private record Retention(BigDecimal retefuente, BigDecimal ica) {
        BigDecimal total() {
            return retefuente.add(ica);
        }
    }

    /**
     * Computes retefuente + ICA on the purchase base.
     *
     * <p><b>Retefuente rule:</b> among the active {@code PurchaseRetentionConfig} entries whose
     * {@code baseMin} is satisfied by the base and whose {@code appliesToPersonType}/
     * {@code appliesToTaxRegime} match the supplier (a null/blank value is a wildcard), the one
     * with the lowest {@code sortOrder} wins (ties broken by code); its {@code rate} is applied on
     * the base. When none applies the retefuente is {@code 0}.</p>
     *
     * <p><b>ICA rule:</b> {@code company_config.purchase_ica_rate} applied on the same base
     * (default {@code 0} when no config exists).</p>
     */
    private Retention computeRetention(BigDecimal base, ThirdParty supplier) {
        var retefuente = retentionConfigRepository.findAllActive().stream()
                .filter(PurchaseRetentionConfig::active)
                .filter(config -> config.baseMin() == null
                        || base.compareTo(config.baseMin()) >= 0)
                .filter(config -> appliesToPersonType(config, supplier))
                .filter(config -> appliesToTaxRegime(config, supplier))
                .min(Comparator.comparingInt(PurchaseRetentionConfig::sortOrder)
                        .thenComparing(PurchaseRetentionConfig::code,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(config -> percentage(base, config.rate()))
                .orElse(BigDecimal.ZERO);

        var icaRate = companyConfigRepository.findConfig()
                .map(CompanyConfig::purchaseIcaRate)
                .orElse(BigDecimal.ZERO);
        var ica = icaRate == null ? BigDecimal.ZERO : percentage(base, icaRate);

        return new Retention(retefuente, ica);
    }

    /** Applies a percentage rate on a base, rounded to 2 decimals (HALF_UP). */
    private static BigDecimal percentage(BigDecimal base, BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        return base.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** A null/blank configured person type is a wildcard; otherwise it must match the supplier. */
    private static boolean appliesToPersonType(PurchaseRetentionConfig config, ThirdParty supplier) {
        var configured = config.appliesToPersonType();
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return supplier.personType() != null
                && configured.trim().equalsIgnoreCase(supplier.personType().name());
    }

    /** A null/blank configured tax regime is a wildcard; otherwise it must match the supplier. */
    private static boolean appliesToTaxRegime(PurchaseRetentionConfig config, ThirdParty supplier) {
        var configured = config.appliesToTaxRegime();
        if (configured == null || configured.isBlank()) {
            return true;
        }
        if (supplier.taxRegime() == null) {
            return false;
        }
        return matchesRegime(configured.trim(), supplier.taxRegime());
    }

    /**
     * Matches a configured tax-regime label against the supplier regime. The domain only models
     * {@code ORDINARIO}/{@code SIMPLE}; the Colombian "régimen común" (config value {@code COMUN})
     * is the ordinary regime, so both labels apply to {@code ORDINARIO}.
     */
    private static boolean matchesRegime(String configured, ThirdParty.TaxRegime regime) {
        return switch (regime) {
            case ORDINARIO -> configured.equalsIgnoreCase("ORDINARIO")
                    || configured.equalsIgnoreCase("COMUN");
            case SIMPLE -> configured.equalsIgnoreCase("SIMPLE");
        };
    }

    @Transactional
    public SupplierInvoiceResponse reconcile(UUID id) {
        var existing = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura de proveedor", id));

        if (existing.status() != InvoiceStatus.PENDING) {
            throw new BusinessException("INV_INVALID_TRANSITION",
                    "Solo se pueden conciliar facturas en estado PENDING. " +
                    "Estado actual: " + existing.status());
        }

        var reconciled = new SupplierInvoice(
                existing.id(),
                existing.supplierId(),
                existing.invoiceNumber(),
                existing.issueDate(),
                existing.dueDate(),
                existing.subtotal(),
                existing.ivaTotal(),
                existing.retentionTotal(),
                existing.total(),
                InvoiceStatus.RECONCILED,
                existing.notes(),
                existing.createdBy(),
                existing.createdAt(),
                null,
                existing.version(),
                existing.ocIds()
        );

        var saved = invoiceRepository.save(reconciled);

        // Create AccountsPayable record for this reconciled invoice
        accountsPayableUseCase.createFromInvoice(saved);

        return SupplierInvoiceResponse.from(saved);
    }

    @Transactional
    public SupplierInvoiceResponse dispute(UUID id, String reason) {
        var existing = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura de proveedor", id));

        if (existing.status() != InvoiceStatus.PENDING &&
            existing.status() != InvoiceStatus.RECONCILED) {
            throw new BusinessException("INV_INVALID_TRANSITION",
                    "Solo se pueden disputar facturas en estado PENDING o RECONCILED. " +
                    "Estado actual: " + existing.status());
        }

        var disputedNotes = (existing.notes() != null ? existing.notes() + "\n" : "")
                + "DISPUTED: " + (reason != null ? reason : "Sin motivo especificado");

        var disputed = new SupplierInvoice(
                existing.id(),
                existing.supplierId(),
                existing.invoiceNumber(),
                existing.issueDate(),
                existing.dueDate(),
                existing.subtotal(),
                existing.ivaTotal(),
                existing.retentionTotal(),
                existing.total(),
                InvoiceStatus.DISPUTED,
                disputedNotes,
                existing.createdBy(),
                existing.createdAt(),
                null,
                existing.version(),
                existing.ocIds()
        );

        return SupplierInvoiceResponse.from(invoiceRepository.save(disputed));
    }

    private boolean isSupplier(ThirdParty tp) {
        if (tp.type() == ThirdParty.ThirdPartyType.SUPPLIER ||
            tp.type() == ThirdParty.ThirdPartyType.BOTH) {
            return true;
        }
        if (tp.thirdPartyCategoryId() != null) {
            return thirdPartyCategoryRepository.findById(tp.thirdPartyCategoryId())
                    .map(cat -> "SUPPLIER".equals(cat.baseType()) || "BOTH".equals(cat.baseType()))
                    .orElse(false);
        }
        return false;
    }
}
