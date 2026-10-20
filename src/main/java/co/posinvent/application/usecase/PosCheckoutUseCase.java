package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.SalePaymentRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ShiftRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.service.FefoPicker;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class PosCheckoutUseCase {

    private final SalesDocumentRepository documentRepo;
    private final SalePaymentRepository salePaymentRepo;
    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final PriceEngineService priceEngine;
    private final FefoPicker fefoPicker;
    private final RecordMovementUseCase recordMovement;
    private final CostingOrchestrator costingService;
    private final ApplicationEventPublisher eventPublisher;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;
    private final DianNumberingService numberingService;
    private final DiscountEngineService discountEngine;
    private final CurrentActorProvider actorProvider;
    private final AuthorizeDiscountUseCase authorizeDiscountUseCase;
    private final ShiftRepository shiftRepo;

    public PosCheckoutUseCase(
            SalesDocumentRepository documentRepo,
            SalePaymentRepository salePaymentRepo,
            StockRepository stockRepo,
            ProductRepository productRepo,
            PriceEngineService priceEngine,
            FefoPicker fefoPicker,
            RecordMovementUseCase recordMovement,
            CostingOrchestrator costingService,
            ApplicationEventPublisher eventPublisher,
            OptimisticConcurrencyExecutor concurrencyExecutor,
            DianNumberingService numberingService,
            DiscountEngineService discountEngine,
            CurrentActorProvider actorProvider,
            AuthorizeDiscountUseCase authorizeDiscountUseCase,
            ShiftRepository shiftRepo
    ) {
        this.documentRepo = documentRepo;
        this.salePaymentRepo = salePaymentRepo;
        this.stockRepo = stockRepo;
        this.productRepo = productRepo;
        this.priceEngine = priceEngine;
        this.fefoPicker = fefoPicker;
        this.recordMovement = recordMovement;
        this.costingService = costingService;
        this.eventPublisher = eventPublisher;
        this.concurrencyExecutor = concurrencyExecutor;
        this.numberingService = numberingService;
        this.discountEngine = discountEngine;
        this.actorProvider = actorProvider;
        this.authorizeDiscountUseCase = authorizeDiscountUseCase;
        this.shiftRepo = shiftRepo;
    }

    /**
     * Entry point for POS checkout. The entire unit of work runs inside
     * {@link OptimisticConcurrencyExecutor} so an optimistic-lock conflict on the
     * shared stock row re-runs stock + invoice + order in one fresh transaction.
     *
     * <p>No {@code @Transactional} here on purpose: the executor owns the
     * {@code REQUIRES_NEW} transaction, so each retry is atomic (stock and invoice
     * commit or roll back together). Wrapping only the stock loop would nest a
     * {@code REQUIRES_NEW} transaction and break that atomicity.</p>
     */
    public CheckoutResponse checkout(CheckoutRequest request) {
        // Resolve the authenticated actor once, on the caller's thread, so the retry
        // executor never depends on a thread-local security context.
        var actor = actorProvider.current();
        return concurrencyExecutor.execute(() -> doCheckout(request, actor));
    }

    private CheckoutResponse doCheckout(CheckoutRequest request, CurrentActorProvider.CurrentActor actor) {
        // 1. Load ORDER, verify status=CONFIRMED
        var order = documentRepo.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", request.orderId()));

        if (order.type() != SalesDocumentType.ORDER) {
            throw new BusinessException("POS_NOT_ORDER",
                    "El documento no es un pedido. Tipo: " + order.type());
        }
        if (order.status() != SalesDocumentStatus.CONFIRMED) {
            throw new BusinessException("POS_ORDER_NOT_CONFIRMED",
                    "El pedido debe estar CONFIRMED para checkout. Estado actual: " + order.status());
        }
        if (order.items().isEmpty()) {
            throw new BusinessException("POS_NO_ITEMS",
                    "El pedido no tiene ítems para facturar.");
        }

        // 1b. Every sale must belong to an open shift. The shift is the source of truth
        //     for the cash register: a request may omit it, but never contradict it.
        var shift = loadAndValidateShift(request, order, actor);

        // 2. Load items and resolve prices, then apply discounts BEFORE IVA:
        //    active promotion (automatic) + the manual discount carried by the order line.
        var today = LocalDate.now();
        var lineInputs = new ArrayList<DiscountEngineService.LineInput>();
        var orderLines = new ArrayList<SaleItem>();
        for (var item : order.items()) {
            var price = priceEngine.resolvePrice(item.productId(), order.clientId());
            var gross = item.quantity().multiply(price.unitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            var promo = discountEngine.activePromotion(item.productId(), today).orElse(null);
            var promoPct = promo == null ? BigDecimal.ZERO : promo.discountPct();

            lineInputs.add(new DiscountEngineService.LineInput(
                    promo == null ? null : promo.id(), gross, price.taxRate(), promoPct,
                    item.discountType(), item.discountValue()));

            orderLines.add(new SaleItem(
                    null, null, item.productId(),
                    item.quantity(), price.unitPrice(),
                    price.taxType(), price.taxRate(), BigDecimal.ZERO, gross,
                    item.lineNumber(), item.batchId(),
                    item.discountType(), item.discountValue(), BigDecimal.ZERO,
                    promo == null ? null : promo.id()
            ));
        }

        // 2b. Compute line discounts, prorated document discount and taxes (0/5/19).
        var computed = discountEngine.calculate(
                lineInputs, request.documentDiscountType(), request.documentDiscountValue());

        // 2c. Discount policy: a manual discount above the cashier ceiling or a total
        //     discount above the global ceiling requires an administrator authorization.
        //     The admin approves out-of-band and hands the cashier a single-use token;
        //     the checkout carries only that token id, never the admin credentials.
        //     When no authorization is required the token (if any) is ignored.
        UUID discountAuthorizedBy = null;
        if (computed.discountTotal().signum() > 0
                && discountEngine.requiresDiscountAuthorization(
                        actor.primaryRoleForDiscountPolicy(),
                        discountEngine.manualDiscountPct(lineInputs),
                        discountEngine.totalDiscountPct(lineInputs, computed.discountTotal()))) {
            if (request.discountAuthorizationId() == null) {
                throw new BusinessException("DISCOUNT_AUTHORIZATION_REQUIRED",
                        "El descuento requiere autorización de un administrador");
            }
            discountAuthorizedBy = authorizeDiscountUseCase.consume(request.discountAuthorizationId());
        }

        // 3 & 4. Decrement stock using FEFO: pick batches by expiration date
        var touchedProductIds = new HashSet<UUID>();
        for (var item : orderLines) {
            touchedProductIds.add(item.productId());

            // FEFO: pick batches by expiration date
            var allocations = fefoPicker.pick(item.productId(), order.warehouseId(), item.quantity());

            // Decrement stock and record movement for each batch consumed
            for (var alloc : allocations) {
                var stock = stockRepo.findByProductBatchWarehouse(
                        item.productId(), alloc.batchId(), order.warehouseId())
                        .orElseThrow(() -> new ResourceNotFoundException("Stock", item.productId()));

                var previousQty = stock.currentQuantity();
                var newQty = previousQty.subtract(alloc.quantity());
                // Consume the reservation booked on CONFIRMED. Clamp at zero so an
                // order that was never reserved does not drive committed below zero.
                var newCommitted = stock.committedQuantity().subtract(alloc.quantity())
                        .max(BigDecimal.ZERO);

                stockRepo.save(new InventoryStock(
                        stock.id(),
                        stock.productId(),
                        stock.batchId(),
                        stock.warehouseId(),
                        newQty,
                        newCommitted,
                        stock.unitCost(),
                        stock.createdAt(),
                        null
                ));

                recordMovement.record(
                        item.productId(), alloc.batchId(), order.warehouseId(),
                        MovementType.EXIT,
                        alloc.quantity(), alloc.unitCost(),
                        previousQty, newQty,
                        "SALE", order.id(),
                        "Venta #" + order.id()
                );

                costingService.resolveCostOnExit(item.productId(), alloc.batchId(), order.warehouseId(), alloc.quantity());
            }
        }

        // Recalculate totalStock for affected products (once per product, not per batch)
        for (var productId : touchedProductIds) {
            productRepo.recalculateTotalStock(productId);
        }

        // 5. Create INVOICE document with the next fiscal number from the active DIAN resolution
        var docNumber = numberingService.nextInvoiceNumber();

        // Materialize the persisted lines: gross subtotal, line discount and the final
        // tax computed on the (prorated) discounted base.
        var persistedItems = new ArrayList<SaleItem>();
        for (int i = 0; i < orderLines.size(); i++) {
            var base = orderLines.get(i);
            var line = computed.lines().get(i);
            persistedItems.add(new SaleItem(
                    null, null, base.productId(),
                    base.quantity(), base.unitPrice(),
                    base.taxType(), base.taxRate(), line.taxAmount(), base.subtotal(),
                    base.lineNumber(), base.batchId(),
                    base.discountType(), base.discountValue(), line.lineDiscount(), line.promotionId()
            ));
        }

        var totalPaid = request.payments().stream()
                .map(CheckoutRequest.PaymentLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPaid.compareTo(computed.total()) < 0) {
            throw new BusinessException("POS_UNDERPAID",
                    "El monto pagado (" + totalPaid + ") es menor al total (" + computed.total() + ")");
        }

        var invoice = new SalesDocument(
                null,
                SalesDocumentType.INVOICE,
                SalesDocumentStatus.DRAFT,
                docNumber,
                order.clientId(),
                order.warehouseId(),
                request.shiftId(),
                shift.cashRegisterId(),
                request.orderId(),
                computed.net(),
                computed.tax0(),
                computed.tax5(),
                computed.tax19(),
                computed.total(),
                actor.userId(),
                null,
                null,
                persistedItems,
                null,   // dueDate — POS invoices have no credit
                false,  // isCreditSale — POS invoices are always cash
                null,   // reason
                computed.discountTotal(),
                computed.documentDiscount(),
                request.documentDiscountType(),
                request.documentDiscountValue(),
                discountAuthorizedBy,
                request.discountReason()
        );

        // The invoice lines are persisted exactly once, by the document cascade:
        // SalesDocumentMapper.toEntity maps items() and SalesDocumentRepositoryAdapter
        // links each one to the saved document. No separate per-item save here, otherwise
        // every line would be inserted twice.
        var savedInvoice = documentRepo.save(invoice);

        // Persist one tender line per requested payment, linked to the invoice.
        // This is the historical payment trace the cash-drawer reconciliation needs.
        for (var line : request.payments()) {
            salePaymentRepo.save(new SalePayment(
                    null, savedInvoice.id(), line.method(), line.amount(),
                    actor.userId(), null, null
            ));
        }

        // 6. Set ORDER status = INVOICED
        var invoicedOrder = new SalesDocument(
                order.id(), order.type(), SalesDocumentStatus.INVOICED,
                order.documentNumber(), order.clientId(), order.warehouseId(),
                request.shiftId(), shift.cashRegisterId(), order.sourceDocumentId(),
                order.totalNet(), order.totalTax0(), order.totalTax5(), order.totalTax19(),
                order.totalAmount(), order.createdBy(), order.createdAt(), order.updatedAt(),
                order.items(), order.dueDate(), order.isCreditSale(), order.reason(),
                order.discountTotal(), order.documentDiscount(), order.documentDiscountType(),
                order.documentDiscountValue(), order.discountAuthorizedBy(), order.discountReason()
        );
        documentRepo.save(invoicedOrder);

        // 7. Calculate change
        var change = totalPaid.subtract(computed.total()).max(BigDecimal.ZERO);

        // 8. Publish accounting + DIAN event with per-rate tax amounts (0/5/19).
        eventPublisher.publishEvent(new InvoiceIssuedEvent(this, savedInvoice.id(), savedInvoice.documentNumber(),
                computed.net(),
                computed.tax0(), computed.tax5(), computed.tax19(),
                computed.total()));

        // 9. Return INVOICE
        return CheckoutResponse.from(documentRepo.findById(savedInvoice.id())
                .orElseThrow(() -> new ResourceNotFoundException("Factura", savedInvoice.id())), change);
    }

    private Shift loadAndValidateShift(
            CheckoutRequest request, SalesDocument order,
            CurrentActorProvider.CurrentActor actor) {
        var shift = shiftRepo.findById(request.shiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Turno", request.shiftId()));

        if (shift.status() != ShiftStatus.OPEN) {
            throw new BusinessException("POS_SHIFT_NOT_OPEN",
                    "El turno no está abierto. Abrí un turno antes de vender.");
        }
        if (request.cashRegisterId() != null
                && !request.cashRegisterId().equals(shift.cashRegisterId())) {
            throw new BusinessException("POS_SHIFT_REGISTER_MISMATCH",
                    "La caja de la venta no coincide con la caja del turno.");
        }
        if (order.shiftId() != null && !order.shiftId().equals(request.shiftId())) {
            throw new BusinessException("POS_SHIFT_MISMATCH",
                    "El pedido pertenece a otro turno.");
        }

        // Single source of truth: the token roles must authorize the shift. An
        // administrator may operate any shift; everyone else must be its effective owner.
        if (!actor.isAdmin()
                && (actor.userId() == null || !actor.userId().equals(shift.effectiveOwnerId()))) {
            SecurityAuditLogger.denied(actor, "CHECKOUT", request.shiftId());
            throw new AccessDeniedException("El turno no pertenece al usuario.");
        }
        return shift;
    }
}
