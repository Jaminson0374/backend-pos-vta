package co.posinvent.application.usecase;

import co.posinvent.application.dto.DevolutionRequest;
import co.posinvent.application.dto.DevolutionResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.StockRepository;
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
import java.util.UUID;

@Service
public class PosDevolutionUseCase {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SalesDocumentRepository documentRepo;
    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final RecordMovementUseCase recordMovement;
    private final AccountsReceivableRepository arRepo;
    private final ApplicationEventPublisher eventPublisher;

    public PosDevolutionUseCase(
            SalesDocumentRepository documentRepo,
            StockRepository stockRepo,
            ProductRepository productRepo,
            RecordMovementUseCase recordMovement,
            AccountsReceivableRepository arRepo,
            ApplicationEventPublisher eventPublisher
    ) {
        this.documentRepo = documentRepo;
        this.stockRepo = stockRepo;
        this.productRepo = productRepo;
        this.recordMovement = recordMovement;
        this.arRepo = arRepo;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DevolutionResponse processDevolution(DevolutionRequest request, UUID userId) {
        // 1. Load original invoice, validate type=INVOICE, status=ISSUED
        var invoice = documentRepo.findById(request.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Factura", request.invoiceId()));

        if (invoice.type() != SalesDocumentType.INVOICE) {
            throw new BusinessException("DEV_NOT_INVOICE",
                    "El documento no es una factura. Tipo: " + invoice.type());
        }
        if (invoice.status() != SalesDocumentStatus.ISSUED) {
            throw new BusinessException("DEV_NOT_ISSUED",
                    "La factura debe estar ISSUED para procesar devolución. Estado actual: " + invoice.status());
        }

        // 2. Check that no prior credit note already exists for this invoice
        var existingCreditNotes = documentRepo.findBySourceDocumentIdAndType(
                request.invoiceId(), SalesDocumentType.CREDIT_NOTE);
        if (!existingCreditNotes.isEmpty()) {
            throw new BusinessException("DEV_DUPLICATE_CN",
                    "La factura ya tiene una nota crédito asociada");
        }

        // 3. Validate each item exists in invoice with sufficient quantity
        var invoiceLines = invoice.items();
        // The document discount is prorated per invoice line with the same formula the checkout
        // engine used, so a refund matches the amount actually charged (see DiscountEngineService).
        var docShares = proratedDocumentShares(invoiceLines, invoice.documentDiscount());
        var totalReturned = BigDecimal.ZERO.setScale(2);
        var returnItems = new ArrayList<SaleItem>();
        int lineNumber = 1;

        for (var devItem : request.items()) {
            var qty = devItem.quantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("DEV_INVALID_QTY",
                        "La cantidad a devolver debe ser mayor a cero para el producto " + devItem.productId());
            }

            // Find matching item in invoice (first line wins for a duplicated product)
            var lineIndex = indexOfProduct(invoiceLines, devItem.productId());
            if (lineIndex < 0) {
                throw new BusinessException("DEV_ITEM_NOT_IN_INVOICE",
                        "El producto " + devItem.productId() + " no está en la factura original");
            }
            var invoiceItem = invoiceLines.get(lineIndex);

            if (qty.compareTo(invoiceItem.quantity()) > 0) {
                throw new BusinessException("DEV_EXCESS_QTY",
                        "Cantidad a devolver (" + qty + ") excede la facturada ("
                        + invoiceItem.quantity() + ") para producto " + devItem.productId());
            }

            // Refund the NET PAID, not the list price: the invoice line already reflects its line
            // discount plus its prorated share of the document discount. Using unitPrice() here
            // would over-refund every discounted sale.
            var lineNetBeforeDoc = lineNetBeforeDoc(invoiceItem);
            var docShare = docShares.get(lineIndex);
            var lineNetPaid = lineNetBeforeDoc.subtract(docShare).max(BigDecimal.ZERO);
            var lineTax = invoiceItem.taxAmount() != null ? invoiceItem.taxAmount() : BigDecimal.ZERO;

            // Proportional to the returned quantity (e.g. 1 of 2 units → 0.5).
            var fraction = qty.divide(invoiceItem.quantity(), 6, RoundingMode.HALF_UP);
            var refundNet = fraction.multiply(lineNetPaid).setScale(2, RoundingMode.HALF_UP);
            var refundTax = fraction.multiply(lineTax).setScale(2, RoundingMode.HALF_UP);

            // Effective unit net after discounts (informational; the line net is already net).
            var unitPrice = refundNet.divide(qty, 2, RoundingMode.HALF_UP);

            // Credit note items use negative quantities. Discount fields stay NONE/0: the credit
            // line's subtotal IS the net paid, so carrying a discount would double-count it.
            var creditItem = new SaleItem(
                    null, null, devItem.productId(),
                    qty.negate(), unitPrice,
                    invoiceItem.taxType(), invoiceItem.taxRate(), refundTax.negate(), refundNet.negate(),
                    lineNumber++, invoiceItem.batchId()
            );
            returnItems.add(creditItem);
            // Net reversed (tax-excluded) drives the AR reduction below.
            totalReturned = totalReturned.add(refundNet);
        }

        // 4. Create CREDIT_NOTE document
        var docNumber = generateCreditNoteNumber();
        var totals = calculateTotals(returnItems);

        var creditNote = new SalesDocument(
                null,
                SalesDocumentType.CREDIT_NOTE,
                SalesDocumentStatus.ISSUED,
                docNumber,
                invoice.clientId(),
                invoice.warehouseId(),
                invoice.shiftId(),
                invoice.cashRegisterId(),
                request.invoiceId(),
                totals.net(),
                totals.tax0(),
                totals.tax5(),
                totals.tax19(),
                totals.total(),
                userId,
                null,
                null,
                returnItems,
                null,
                false,
                request.reason()
        );

        // The credit-note lines are persisted exactly once, by the document cascade:
        // SalesDocumentMapper.toEntity maps items() and SalesDocumentRepositoryAdapter
        // links each one to the saved document. No separate per-item save here, otherwise
        // every line would be inserted twice (same defect fixed in PosCheckoutUseCase).
        var savedCreditNote = documentRepo.save(creditNote);

        // 5. Reverse inventory: for each item, increment stock and record RETURN movement
        for (var devItem : request.items()) {
            var invoiceItem = invoice.items().stream()
                    .filter(i -> i.productId().equals(devItem.productId()))
                    .findFirst().orElseThrow();

            var fallbackBatchId = invoiceItem.batchId();
            var stock = stockRepo.findByProductBatchWarehouse(
                            devItem.productId(), fallbackBatchId, invoice.warehouseId())
                    .orElseGet(() -> {
                        var now = OffsetDateTime.now();
                        return stockRepo.save(new InventoryStock(
                                null, devItem.productId(), fallbackBatchId, invoice.warehouseId(),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, now, now));
                    });

            var previousQty = stock.currentQuantity();
            var newQty = stock.currentQuantity().add(devItem.quantity());

            var updated = new InventoryStock(
                    stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                    newQty,
                    stock.committedQuantity(),
                    stock.unitCost(), stock.createdAt(), stock.updatedAt()
            );
            stockRepo.save(updated);

            recordMovement.record(
                    devItem.productId(), fallbackBatchId, invoice.warehouseId(),
                    MovementType.ENTRY,
                    devItem.quantity(), stock.unitCost(),
                    previousQty, newQty,
                    "SALE", savedCreditNote.id(),
                    "Devolución #" + savedCreditNote.id()
            );
        }

        // Recalculate totalStock for affected products
        var touchedProductIds = new HashSet<UUID>();
        for (var devItem : request.items()) {
            if (touchedProductIds.add(devItem.productId())) {
                productRepo.recalculateTotalStock(devItem.productId());
            }
        }

        // 6. If original invoice was credit sale, reduce AR outstanding
        var arAdjustment = BigDecimal.ZERO.setScale(2);
        if (Boolean.TRUE.equals(invoice.isCreditSale())) {
            var arOpt = arRepo.findByDocumentId(invoice.id());
            if (arOpt.isPresent()) {
                var ar = arOpt.get();
                var adjustmentAmount = ar.outstanding().min(totalReturned);
                arAdjustment = adjustmentAmount;
                var newOutstanding = ar.outstanding().subtract(adjustmentAmount);
                var newStatus = AccountsReceivable.computeStatus(ar.totalAmount(), ar.paidAmount());

                var updatedAr = new AccountsReceivable(
                        ar.id(), ar.clientId(), ar.documentId(),
                        ar.totalAmount(), ar.paidAmount(), newOutstanding,
                        ar.dueDate(), newStatus,
                        ar.createdAt(), null,
                        ar.interestRate(), ar.interestAmount(), ar.lastInterestCalcDate()
                );
                arRepo.save(updatedAr);
            }
        }

        // 7. Publish event (credit note) with per-rate tax amounts (0/5/19).
        eventPublisher.publishEvent(new InvoiceIssuedEvent(this, savedCreditNote.id(), savedCreditNote.documentNumber(),
                savedCreditNote.totalNet(),
                savedCreditNote.totalTax0(), savedCreditNote.totalTax5(),
                savedCreditNote.totalTax19(),
                savedCreditNote.totalAmount()));

        // 8. Build response
        return new DevolutionResponse(
                savedCreditNote.id(),
                savedCreditNote.documentNumber(),
                savedCreditNote.totalAmount(),
                request.items().size(),
                arAdjustment
        );
    }

    /** Index of the first invoice line for a product, or {@code -1} when absent. */
    private static int indexOfProduct(List<SaleItem> lines, UUID productId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).productId().equals(productId)) {
                return i;
            }
        }
        return -1;
    }

    /** Line net before the document discount: gross subtotal minus the line discount (never negative). */
    private static BigDecimal lineNetBeforeDoc(SaleItem item) {
        var subtotal = item.subtotal() != null ? item.subtotal() : BigDecimal.ZERO;
        var discount = item.discountAmount() != null ? item.discountAmount() : BigDecimal.ZERO;
        return subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Prorates the document discount across all invoice lines proportionally to each line net,
     * pushing the rounding residue onto the last line so the shares add up to the document
     * discount exactly. Mirrors {@code DiscountEngineService} / the checkout calculation, so a
     * refund reverts the same amount that was charged.
     *
     * <p>Returns {@code 0} for every line when the document discount is zero or when the invoice
     * has no discounted net.</p>
     */
    private static List<BigDecimal> proratedDocumentShares(List<SaleItem> lines, BigDecimal documentDiscount) {
        var shares = new ArrayList<BigDecimal>(lines.size());
        var total = BigDecimal.ZERO.setScale(2);
        for (var line : lines) {
            total = total.add(lineNetBeforeDoc(line));
        }

        var document = documentDiscount != null ? documentDiscount : BigDecimal.ZERO;
        var allocated = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < lines.size(); i++) {
            BigDecimal share;
            if (document.signum() == 0 || total.signum() == 0) {
                share = BigDecimal.ZERO.setScale(2);
            } else if (i == lines.size() - 1) {
                share = document.subtract(allocated).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            } else {
                share = document.multiply(lineNetBeforeDoc(lines.get(i)))
                        .divide(total, 2, RoundingMode.HALF_UP);
                allocated = allocated.add(share);
            }
            shares.add(share);
        }
        return shares;
    }

    private String generateCreditNoteNumber() {
        var today = LocalDate.now().format(DATE_FMT);
        return "NC-" + today + "-" + System.currentTimeMillis() % 100000;
    }

    private record Totals(
            BigDecimal net, BigDecimal tax0, BigDecimal tax5,
            BigDecimal tax19, BigDecimal total
    ) {}

    private Totals calculateTotals(List<SaleItem> items) {
        var tax0 = BigDecimal.ZERO.setScale(2);
        var tax5 = BigDecimal.ZERO.setScale(2);
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
            } else {
                tax19 = tax19.add(amount);
            }
        }

        var total = net.add(tax0).add(tax5).add(tax19);
        return new Totals(net, tax0, tax5, tax19, total);
    }
}
