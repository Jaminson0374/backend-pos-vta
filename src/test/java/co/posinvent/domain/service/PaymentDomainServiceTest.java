package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.Payment.InvoicePayment;
import co.posinvent.domain.model.SupplierInvoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentDomainServiceTest {

    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID OTHER_INVOICE_ID = UUID.randomUUID();

    private PaymentDomainService service;

    @BeforeEach
    void setUp() {
        service = new PaymentDomainService();
    }

    @Test
    void shouldAcceptPositiveAmount() {
        assertThatCode(() -> service.validateAmount(BigDecimal.ONE)).doesNotThrowAnyException();
        assertThatCode(() -> service.validateAmount(new BigDecimal("0.01"))).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullAmount() {
        assertInvalidAmount(null);
    }

    @Test
    void shouldRejectZeroAmount() {
        assertInvalidAmount(BigDecimal.ZERO);
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertInvalidAmount(new BigDecimal("-0.01"));
    }

    @Test
    void shouldAcceptReconciledInvoiceFullyAppliedWithinPending() {
        var invoice = reconciledInvoice("500");
        var payments = List.of(new InvoicePayment(INVOICE_ID, new BigDecimal("500.00")));

        assertThatCode(() -> service.validateInvoicePayments(
                List.of(invoice), payments, new BigDecimal("500"), Map.of(INVOICE_ID, new BigDecimal("500"))))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNonReconciledInvoice() {
        var invoice = invoice(InvoiceStatus.PENDING, "100");
        var payments = List.of(new InvoicePayment(INVOICE_ID, new BigDecimal("100")));

        assertThatThrownBy(() -> service.validateInvoicePayments(
                List.of(invoice), payments, new BigDecimal("100"), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVOICE_NOT_PAYABLE")
                .hasMessageContaining("no está en estado RECONCILED");
    }

    @Test
    void shouldRejectAppliedSumMismatchBeyondTolerance() {
        var invoice = reconciledInvoice("200");
        var payments = List.of(new InvoicePayment(INVOICE_ID, new BigDecimal("100.02")));

        assertThatThrownBy(() -> service.validateInvoicePayments(
                List.of(invoice), payments, new BigDecimal("100.00"), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_PAYMENT_AMOUNT")
                .hasMessageContaining("no coincide con el total del pago");
    }

    @Test
    void shouldAcceptAppliedSumDifferenceAtTolerance() {
        var invoice = reconciledInvoice("200");
        var payments = List.of(new InvoicePayment(INVOICE_ID, new BigDecimal("100.01")));

        assertThatCode(() -> service.validateInvoicePayments(
                List.of(invoice), payments, new BigDecimal("100.00"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDuplicateInvoiceLinesSummingBeyondPending() {
        var invoice = reconciledInvoice("100");
        var payments = List.of(
                new InvoicePayment(INVOICE_ID, new BigDecimal("60")),
                new InvoicePayment(INVOICE_ID, new BigDecimal("60")));

        assertThatThrownBy(() -> service.validateInvoicePayments(
                List.of(invoice), payments, new BigDecimal("120"), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "OVERPAYMENT")
                .hasMessageContaining("excede el saldo pendiente");
    }

    @Test
    void shouldAcceptDuplicateInvoiceLinesSummingWithinPending() {
        var invoice = reconciledInvoice("100");
        var payments = List.of(
                new InvoicePayment(INVOICE_ID, new BigDecimal("40")),
                new InvoicePayment(INVOICE_ID, new BigDecimal("60")));

        assertThatCode(() -> service.validateInvoicePayments(
                List.of(invoice), payments, new BigDecimal("100"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNonPositivePerInvoiceAppliedAmount() {
        var invoice = reconciledInvoice("0");
        var payments = List.of(new InvoicePayment(INVOICE_ID, BigDecimal.ZERO));

        assertThatThrownBy(() -> service.validateInvoicePayments(
                List.of(invoice), payments, BigDecimal.ZERO, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_PAYMENT_AMOUNT")
                .hasMessageContaining("debe ser mayor a cero");
    }

    @Test
    void shouldRejectAppliedAmountAbovePendingBalance() {
        var invoice = reconciledInvoice("1000");
        var payments = List.of(new InvoicePayment(INVOICE_ID, new BigDecimal("400")));

        assertThatThrownBy(() -> service.validateInvoicePayments(
                List.of(invoice), payments, new BigDecimal("400"), Map.of(INVOICE_ID, new BigDecimal("300"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "OVERPAYMENT")
                .hasMessageContaining("excede el saldo pendiente");
    }

    @Test
    void shouldFallBackToInvoiceTotalWhenPendingMapIsNull() {
        var invoice = reconciledInvoice("1000");
        var payments = List.of(new InvoicePayment(INVOICE_ID, new BigDecimal("1000")));

        assertThatCode(() -> service.validateInvoicePayments(
                List.of(invoice), payments, new BigDecimal("1000"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldFallBackToInvoiceTotalWhenPendingMapHasNoEntry() {
        var invoice = reconciledInvoice("1000");
        var payments = List.of(new InvoicePayment(INVOICE_ID, new BigDecimal("1000")));

        assertThatCode(() -> service.validateInvoicePayments(
                List.of(invoice), payments, new BigDecimal("1000"),
                Map.of(OTHER_INVOICE_ID, new BigDecimal("300"))))
                .doesNotThrowAnyException();
    }

    private void assertInvalidAmount(BigDecimal amount) {
        assertThatThrownBy(() -> service.validateAmount(amount))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_PAYMENT_AMOUNT")
                .hasMessageContaining("El monto del pago debe ser mayor a cero");
    }

    private SupplierInvoice reconciledInvoice(String total) {
        return invoice(InvoiceStatus.RECONCILED, total);
    }

    private SupplierInvoice invoice(InvoiceStatus status, String total) {
        var totalAmount = new BigDecimal(total);
        return new SupplierInvoice(
                INVOICE_ID,
                SUPPLIER_ID,
                "FV-001",
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                totalAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                totalAmount,
                status,
                null,
                null,
                null,
                null,
                0L,
                List.of()
        );
    }
}
