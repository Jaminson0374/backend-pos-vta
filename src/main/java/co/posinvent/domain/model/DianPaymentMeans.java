package co.posinvent.domain.model;

import co.posinvent.domain.exception.BusinessException;

/**
 * DIAN payment means codes used by the fiscal payload. The provider maps these concepts to its own
 * `payment_types` catalog IDs; the two identifiers are related but not interchangeable.
 */
public enum DianPaymentMeans {
    CASH("10", "Efectivo"),
    CHECK("20", "Cheque"),
    TRANSFER("42", "Consignación bancaria"),
    CARD("48", "Tarjeta de crédito");

    private final String code;
    private final String description;

    DianPaymentMeans(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static DianPaymentMeans fromInternal(String method) {
        if (method == null || method.isBlank()) {
            throw new BusinessException("DIAN_UNSUPPORTED_PAYMENT_METHOD",
                    "El medio de pago es obligatorio para la factura electrónica.");
        }
        return switch (method.trim().toUpperCase()) {
            case "CASH", "EFECTIVO" -> CASH;
            case "CHECK", "CHEQUE" -> CHECK;
            case "TRANSFER", "TRANSFERENCIA", "CONSIGNACION", "CONSIGNACIÓN" -> TRANSFER;
            case "CARD", "TARJETA", "CREDIT_CARD" -> CARD;
            default -> throw new BusinessException("DIAN_UNSUPPORTED_PAYMENT_METHOD",
                    "Medio de pago no homologado para DIAN: " + method);
        };
    }
}
