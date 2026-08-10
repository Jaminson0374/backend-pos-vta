package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.service.ReceiptDomainService.ReceiptLineItemInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptDomainServiceTest {

    private ReceiptDomainService service;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID OC_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReceiptDomainService();
    }

    @Test
    void shouldRejectExpirationDateInPast() {
        var oc = new PurchaseOrder(
                OC_ID,
                SUPPLIER_ID,
                PurchaseOrderStatus.PENDING,
                LocalDate.now(),
                "OC-001",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                List.of(new PurchaseLineItem(
                        null, OC_ID, PRODUCT_ID, WAREHOUSE_ID,
                        new BigDecimal("10"), BigDecimal.ZERO,
                        new BigDecimal("100"), null, null, 1
                ))
        );

        var receiptLine = new ReceiptLineItemInput(
                PRODUCT_ID,
                WAREHOUSE_ID,
                new BigDecimal("5"),
                new BigDecimal("500"),
                LocalDate.now().minusDays(1) // yesterday — expired
        );

        assertThatThrownBy(() -> service.validateLines(oc, List.of(receiptLine)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "EXPIRATION_DATE_IN_PAST");
    }
}
