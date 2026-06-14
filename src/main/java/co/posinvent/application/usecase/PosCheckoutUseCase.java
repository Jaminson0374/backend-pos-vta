package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.SaleItemRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PosCheckoutUseCase {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SalesDocumentRepository documentRepo;
    private final SaleItemRepository itemRepo;
    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final PriceEngineService priceEngine;
    private final RecordMovementUseCase recordMovement;
    private final ApplicationEventPublisher eventPublisher;

    public PosCheckoutUseCase(
            SalesDocumentRepository documentRepo,
            SaleItemRepository itemRepo,
            StockRepository stockRepo,
            ProductRepository productRepo,
            PriceEngineService priceEngine,
            RecordMovementUseCase recordMovement,
            ApplicationEventPublisher eventPublisher
    ) {
        this.documentRepo = documentRepo;
        this.itemRepo = itemRepo;
        this.stockRepo = stockRepo;
        this.productRepo = productRepo;
        this.priceEngine = priceEngine;
        this.recordMovement = recordMovement;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request, UUID userId) {
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

        // 2. Load items and resolve prices via PriceEngineService
        var pricedItems = new ArrayList<SaleItem>();
        for (var item : order.items()) {
            var price = priceEngine.resolvePrice(item.productId(), order.clientId());
            var subtotal = item.quantity().multiply(price.unitPrice());
            var taxAmount = subtotal.multiply(price.taxRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            pricedItems.add(new SaleItem(
                    null, null, item.productId(),
                    item.quantity(), price.unitPrice(),
                    price.taxType(), price.taxRate(), taxAmount, subtotal,
                    item.lineNumber(), item.batchId()
            ));
        }

        // 3 & 4. Decrement stock — auto-create if no stock record exists (demo mode)
        for (var item : pricedItems) {
            var fallbackBatchId = item.batchId(); // may be null for products without lots
            var stock = stockRepo.findByProductBatchWarehouse(
                            item.productId(), fallbackBatchId, order.warehouseId())
                    .orElseGet(() -> {
                        var now = OffsetDateTime.now();
                        return stockRepo.save(new InventoryStock(
                                null, item.productId(), fallbackBatchId, order.warehouseId(),
                                new BigDecimal("1000000"),
                                BigDecimal.ZERO, BigDecimal.ZERO, now, now));
                    });

            if (!stock.hasStock(item.quantity())) {
                throw new BusinessException("POS_INSUFFICIENT_STOCK",
                        "Stock insuficiente para producto " + item.productId()
                        + ". Disponible: " + stock.availableQuantity()
                        + ", Requerido: " + item.quantity());
            }

            // Decrement currentQuantity AND committedQuantity (reservation was done on CONFIRMED)
            var previousQty = stock.currentQuantity();
            var newQty = stock.currentQuantity().subtract(item.quantity());
            var updated = new InventoryStock(
                    stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                    newQty,
                    stock.committedQuantity().subtract(item.quantity()).max(BigDecimal.ZERO),
                    stock.unitCost(), stock.createdAt(), stock.updatedAt()
            );
            stockRepo.save(updated);
            recordMovement.record(
                    item.productId(), item.batchId(), order.warehouseId(),
                    MovementType.EXIT,
                    item.quantity(), stock.unitCost(),
                    previousQty, newQty,
                    "SALE", order.id(),
                    "Venta #" + order.id()
            );
        }

        // Recalculate totalStock for affected products
        var touchedProductIds = new HashSet<UUID>();
        for (var item : pricedItems) {
            if (touchedProductIds.add(item.productId())) {
                productRepo.recalculateTotalStock(item.productId());
            }
        }

        // 5. Create INVOICE document
        var docNumber = generateInvoiceNumber();
        var totals = calculateTotals(pricedItems);

        var totalPaid = request.payments().stream()
                .map(CheckoutRequest.PaymentLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPaid.compareTo(totals.total()) < 0) {
            throw new BusinessException("POS_UNDERPAID",
                    "El monto pagado (" + totalPaid + ") es menor al total (" + totals.total() + ")");
        }

        var invoice = new SalesDocument(
                null,
                SalesDocumentType.INVOICE,
                SalesDocumentStatus.DRAFT,
                docNumber,
                order.clientId(),
                order.warehouseId(),
                order.shiftId(),
                request.cashRegisterId(),
                request.orderId(),
                totals.net(),
                totals.tax0(),
                totals.tax5(),
                totals.tax8(),
                totals.tax19(),
                totals.total(),
                userId,
                null,
                null,
                pricedItems,
                null,   // dueDate — POS invoices have no credit
                false,  // isCreditSale — POS invoices are always cash
                null    // reason
        );

        // Save invoice first, then assign documentId to each item
        var savedInvoice = documentRepo.save(invoice);

        for (var item : pricedItems) {
            itemRepo.save(new SaleItem(
                    null, savedInvoice.id(), item.productId(),
                    item.quantity(), item.unitPrice(),
                    item.taxType(), item.taxRate(), item.taxAmount(), item.subtotal(),
                    item.lineNumber(), item.batchId()
            ));
        }

        // 6. Set ORDER status = INVOICED
        var invoicedOrder = new SalesDocument(
                order.id(), order.type(), SalesDocumentStatus.INVOICED,
                order.documentNumber(), order.clientId(), order.warehouseId(),
                order.shiftId(), order.cashRegisterId(), order.sourceDocumentId(),
                order.totalNet(), order.totalTax0(), order.totalTax5(), order.totalTax8(), order.totalTax19(),
                order.totalAmount(), order.createdBy(), order.createdAt(), order.updatedAt(),
                order.items(), order.dueDate(), order.isCreditSale(), order.reason()
        );
        documentRepo.save(invoicedOrder);

        // 7. Calculate change
        var change = totalPaid.subtract(totals.total()).max(BigDecimal.ZERO);

        // 8. Publish accounting + DIAN event with per-rate tax amounts
        eventPublisher.publishEvent(new InvoiceIssuedEvent(this, savedInvoice.id(), savedInvoice.documentNumber(),
                totals.net(),
                totals.tax0(), totals.tax5(), totals.tax8(), totals.tax19(),
                totals.total()));

        // 9. Return INVOICE
        return CheckoutResponse.from(documentRepo.findById(savedInvoice.id())
                .orElseThrow(() -> new ResourceNotFoundException("Factura", savedInvoice.id())), change);
    }

    private String generateInvoiceNumber() {
        var today = LocalDate.now().format(DATE_FMT);
        return "INVOICE-" + today + "-" + System.currentTimeMillis() % 100000;
    }

    private record Totals(
            BigDecimal net, BigDecimal tax0, BigDecimal tax5,
            BigDecimal tax8, BigDecimal tax19, BigDecimal total
    ) {}

    private Totals calculateTotals(List<SaleItem> items) {
        var tax0 = BigDecimal.ZERO.setScale(2);
        var tax5 = BigDecimal.ZERO.setScale(2);
        var tax8 = BigDecimal.ZERO.setScale(2);
        var tax19 = BigDecimal.ZERO.setScale(2);
        var net = BigDecimal.ZERO.setScale(2);

        for (var item : items) {
            net = net.add(item.subtotal());
            var rate = item.taxRate();
            var amount = item.taxAmount();
            if (rate.compareTo(BigDecimal.ZERO) == 0) {
                tax0 = tax0.add(amount);
            } else if (rate.compareTo(new BigDecimal("5")) == 0) {
                tax5 = tax5.add(amount);
            } else if (rate.compareTo(new BigDecimal("8")) == 0) {
                tax8 = tax8.add(amount);
            } else {
                tax19 = tax19.add(amount);
            }
        }

        var total = net.add(tax0).add(tax5).add(tax8).add(tax19);
        return new Totals(net, tax0, tax5, tax8, tax19, total);
    }
}
