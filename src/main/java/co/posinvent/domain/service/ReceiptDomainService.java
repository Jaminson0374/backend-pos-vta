package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validación pura de dominio para la recepción de mercancía.
 * No tiene dependencias de infraestructura ni Spring.
 */
@Service
public final class ReceiptDomainService {

    private static final BigDecimal COST_DEVIATION_THRESHOLD = new BigDecimal("0.20");

    /**
     * Valida que la orden de compra esté en un estado que permita recibir mercancía:
     * PENDING o PARTIAL.
     *
     * @throws BusinessException código OC_NOT_PROCESSABLE si el estado no es válido
     */
    public void validateOcProcessable(PurchaseOrder oc) {
        if (oc.status() != PurchaseOrderStatus.PENDING
                && oc.status() != PurchaseOrderStatus.PARTIAL) {
            throw new BusinessException(
                    "OC_NOT_PROCESSABLE",
                    "La orden de compra no se puede recibir. Estado actual: " + oc.status()
            );
        }
    }

    /**
     * Valida cada línea del receipt contra la orden de compra.
     *
     * @throws BusinessException con códigos LINE_NOT_IN_OC, EXCEEDS_ORDERED_QTY, o INVALID_RECEIPT_VALUE
     */
    public void validateLines(
            PurchaseOrder oc,
            List<ReceiptLineItemInput> receiptLines
    ) {
        if (receiptLines == null || receiptLines.isEmpty()) {
            throw new BusinessException(
                    "EMPTY_RECEIPT_LINES",
                    "La recepción debe tener al menos una línea"
            );
        }

        var ocLineByProduct = oc.lines().stream()
                .collect(Collectors.toMap(PurchaseLineItem::productId, line -> line));

        for (var receiptLine : receiptLines) {
            // El productId debe existir en la OC
            var ocLine = ocLineByProduct.get(receiptLine.productId());
            if (ocLine == null) {
                throw new BusinessException(
                        "LINE_NOT_IN_OC",
                        "El producto " + receiptLine.productId()
                                + " no está en la orden de compra " + oc.id()
                );
            }

            // receivedQty debe ser > 0
            if (receiptLine.receivedQty() == null
                    || receiptLine.receivedQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                        "INVALID_RECEIPT_VALUE",
                        "La cantidad recibida del producto " + receiptLine.productId()
                                + " debe ser mayor a cero"
                );
            }

            // receivedQty ≤ orderedQty − alreadyReceivedQty
            var remaining = ocLine.orderedQty().subtract(ocLine.receivedQty());
            if (receiptLine.receivedQty().compareTo(remaining) > 0) {
                throw new BusinessException(
                        "EXCEEDS_ORDERED_QTY",
                        "La cantidad recibida (" + receiptLine.receivedQty()
                                + ") del producto " + receiptLine.productId()
                                + " excede la cantidad pendiente (" + remaining + ")"
                );
            }

            // actualCost >= 0
            if (receiptLine.actualCost() == null
                    || receiptLine.actualCost().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(
                        "INVALID_RECEIPT_VALUE",
                        "El costo real del producto " + receiptLine.productId()
                                + " no puede ser negativo"
                );
            }

            // expirationDate validation: if provided, must not be in the past
            if (receiptLine.expirationDate() != null
                    && receiptLine.expirationDate().isBefore(LocalDate.now())) {
                throw new BusinessException(
                        "EXPIRATION_DATE_IN_PAST",
                        "La fecha de vencimiento del producto " + receiptLine.productId()
                                + " no puede estar en el pasado: "
                                + receiptLine.expirationDate()
                );
            }
        }
    }

    /**
     * Calcula las líneas donde la desviación entre el costo real y el costo
     * presupuestado de la OC supera el 20 %.
     */
    public List<CostDeviation> computeDeviations(
            PurchaseOrder oc,
            List<ReceiptLineItemInput> receiptLines
    ) {
        var ocLineByProduct = oc.lines().stream()
                .collect(Collectors.toMap(PurchaseLineItem::productId, line -> line));

        var deviations = new ArrayList<CostDeviation>();

        for (var receiptLine : receiptLines) {
            var ocLine = ocLineByProduct.get(receiptLine.productId());
            if (ocLine == null) continue;

            var ocUnitCost = ocLine.unitCost();
            var actualCost = receiptLine.actualCost();

            if (ocUnitCost == null || ocUnitCost.compareTo(BigDecimal.ZERO) == 0) continue;

            var diff = actualCost.subtract(ocUnitCost).abs();
            var deviationPct = diff.divide(ocUnitCost, 6, RoundingMode.HALF_UP);

            if (deviationPct.compareTo(COST_DEVIATION_THRESHOLD) > 0) {
                deviations.add(new CostDeviation(
                        receiptLine.productId(),
                        ocUnitCost,
                        actualCost,
                        deviationPct.multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP)
                ));
            }
        }

        return deviations;
    }

    /**
     * Input para validación de líneas de recepción. Evita que el dominio
     * dependa de los DTO de la capa de aplicación.
     */
    public record ReceiptLineItemInput(
            UUID productId,
            UUID warehouseId,
            BigDecimal receivedQty,
            BigDecimal actualCost,
            LocalDate expirationDate
    ) {}

    public record CostDeviation(
            UUID productId,
            BigDecimal ocUnitCost,
            BigDecimal actualCost,
            BigDecimal deviationPct
    ) {}
}
