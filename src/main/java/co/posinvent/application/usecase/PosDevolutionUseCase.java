package co.posinvent.application.usecase;

import co.posinvent.application.dto.DevolutionRequest;
import co.posinvent.application.dto.DevolutionResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SaleItemRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
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
    private final SaleItemRepository itemRepo;
    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final RecordMovementUseCase recordMovement;
    private final AccountsReceivableRepository arRepo;
    private final ThirdPartyRepository thirdPartyRepo;
    private final ApplicationEventPublisher eventPublisher;

    public PosDevolutionUseCase(
            SalesDocumentRepository documentRepo,
            SaleItemRepository itemRepo,
            StockRepository stockRepo,
            ProductRepository productRepo,
            RecordMovementUseCase recordMovement,
            AccountsReceivableRepository arRepo,
            ThirdPartyRepository thirdPartyRepo,
            ApplicationEventPublisher eventPublisher
    ) {
        this.documentRepo = documentRepo;
        this.itemRepo = itemRepo;
        this.stockRepo = stockRepo;
        this.productRepo = productRepo;
        this.recordMovement = recordMovement;
        this.arRepo = arRepo;
        this.thirdPartyRepo = thirdPartyRepo;
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
        var totalReturned = BigDecimal.ZERO.setScale(2);
        var returnItems = new ArrayList<SaleItem>();
        int lineNumber = 1;

        for (var devItem : request.items()) {
            var qty = devItem.quantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("DEV_INVALID_QTY",
                        "La cantidad a devolver debe ser mayor a cero para el producto " + devItem.productId());
            }

            // Find matching item in invoice
            var invoiceItem = invoice.items().stream()
                    .filter(i -> i.productId().equals(devItem.productId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("DEV_ITEM_NOT_IN_INVOICE",
                            "El producto " + devItem.productId() + " no está en la factura original"));

            if (qty.compareTo(invoiceItem.quantity()) > 0) {
                throw new BusinessException("DEV_EXCESS_QTY",
                        "Cantidad a devolver (" + qty + ") excede la facturada ("
                        + invoiceItem.quantity() + ") para producto " + devItem.productId());
            }

            var unitPrice = invoiceItem.unitPrice();
            var subtotal = qty.multiply(unitPrice);
            var taxAmount = subtotal.multiply(invoiceItem.taxRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Credit note items use negative quantities
            var creditItem = new SaleItem(
                    null, null, devItem.productId(),
                    qty.negate(), unitPrice,
                    invoiceItem.taxType(), invoiceItem.taxRate(), taxAmount.negate(), subtotal.negate(),
                    lineNumber++, invoiceItem.batchId()
            );
            returnItems.add(creditItem);
            totalReturned = totalReturned.add(subtotal);
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
                totals.tax8(),
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

        var savedCreditNote = documentRepo.save(creditNote);

        // Save items with the credit note id
        for (var item : returnItems) {
            itemRepo.save(new SaleItem(
                    null, savedCreditNote.id(), item.productId(),
                    item.quantity(), item.unitPrice(),
                    item.taxType(), item.taxRate(), item.taxAmount(), item.subtotal(),
                    item.lineNumber(), item.batchId()
            ));
        }

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

        // 6. If original invoice was credit sale, reduce AR outstanding and adjust ThirdParty balance
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

                // Reduce ThirdParty.currentBalance by the adjusted amount
                var thirdParty = thirdPartyRepo.findById(ar.clientId());
                if (thirdParty.isPresent()) {
                    var tp = thirdParty.get();
                    var newBalance = tp.currentBalance() != null
                            ? tp.currentBalance().subtract(adjustmentAmount).max(BigDecimal.ZERO)
                            : BigDecimal.ZERO;
                    var updatedTp = new ThirdParty(
                            tp.id(), tp.numIdentification(), tp.name(), tp.type(),
                            tp.priceListId(), tp.creditLimit(), newBalance,
                            tp.personType(), tp.taxRegime(), tp.taxResponsibilities(),
                            tp.cityCode(), tp.dianClassification(), tp.active(),
                            tp.createdAt(), tp.updatedAt(),
                            tp.thirdPartyCategoryId(), tp.identificationTypeId(),
                            tp.dv(), tp.lastName(), tp.commonName(), tp.phone(),
                            tp.address(), tp.departmentId(), tp.cityId(), tp.email(),
                            tp.website(), tp.entryDate(), tp.creditDays(),
                            tp.contactName(), tp.contactPhone(), tp.contactAddress(),
                            tp.contactEmail(), tp.taxContactFirstName(),
                            tp.taxContactLastName(), tp.taxEmail(), tp.billingPhone(),
                            tp.isGranContribuyente(), tp.isAutoretenedor(),
                            tp.isAgenteRetencionIva(), tp.isRegimenSimple(),
                            tp.otherTaxResp(), tp.employeeData()
                    );
                    thirdPartyRepo.save(updatedTp);
                }
            }
        }

        // 7. Publish event (credit note) with per-rate tax amounts
        eventPublisher.publishEvent(new InvoiceIssuedEvent(this, savedCreditNote.id(), savedCreditNote.documentNumber(),
                savedCreditNote.totalNet(),
                savedCreditNote.totalTax0(), savedCreditNote.totalTax5(),
                savedCreditNote.totalTax8(), savedCreditNote.totalTax19(),
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

    private String generateCreditNoteNumber() {
        var today = LocalDate.now().format(DATE_FMT);
        return "NC-" + today + "-" + System.currentTimeMillis() % 100000;
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
