package co.posinvent.domain.model;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain contract for the sales-document state machine.
 *
 * <p>No Spring, no security, no mocks: the entity validates the transition and returns a new
 * immutable instance with only the status replaced. Authorization is deliberately absent here —
 * that belongs to the use case.</p>
 */
class SalesDocumentTransitionTest {

    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-01-15T10:15:30Z");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-01-16T11:16:31Z");

    // ── ORDER ─────────────────────────────────────────────────────────────

    @Test
    void orderDraftToConfirmedReturnsANewDocumentPreservingEveryOtherComponent() {
        var items = List.of(item());
        var draft = document(SalesDocumentType.ORDER, SalesDocumentStatus.DRAFT, items);

        var confirmed = draft.transitionTo(SalesDocumentStatus.CONFIRMED);

        assertThat(confirmed).isNotSameAs(draft);
        assertThat(confirmed.status()).isEqualTo(SalesDocumentStatus.CONFIRMED);
        assertThat(confirmed.id()).isEqualTo(DOC_ID);
        assertThat(confirmed.type()).isEqualTo(SalesDocumentType.ORDER);
        assertThat(confirmed.items()).isSameAs(items);
        assertThat(confirmed.documentNumber()).isEqualTo("ORD-TEST-0001");
        assertThat(confirmed.totalNet()).isEqualByComparingTo("100.00");
        assertThat(confirmed.totalAmount()).isEqualByComparingTo("119.00");
        assertThat(confirmed.createdBy()).isEqualTo(USER_ID);
        assertThat(confirmed.createdAt()).isEqualTo(CREATED_AT);
        assertThat(confirmed.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(confirmed.discountTotal()).isEqualByComparingTo("12.50");
        assertThat(confirmed.documentDiscount()).isEqualByComparingTo("10.00");
        assertThat(confirmed.documentDiscountType()).isEqualTo("AMOUNT");
        assertThat(confirmed.documentDiscountValue()).isEqualByComparingTo("10.00");
        assertThat(confirmed.discountAuthorizedBy()).isEqualTo(USER_ID);
        assertThat(confirmed.discountReason()).isEqualTo("Cliente frecuente");
        assertThat(draft.status()).isEqualTo(SalesDocumentStatus.DRAFT);
    }

    @Test
    void orderDraftToConfirmedWithoutItemsFailsWithNoItems() {
        var draft = document(SalesDocumentType.ORDER, SalesDocumentStatus.DRAFT, List.of());

        assertThatThrownBy(() -> draft.transitionTo(SalesDocumentStatus.CONFIRMED))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("SD_NO_ITEMS");
                    assertThat(ex.getMessage())
                            .isEqualTo("El pedido debe tener al menos un ítem para confirmar");
                });
    }

    @Test
    void orderDraftToIssuedAndToPaidAreInvalid() {
        var draft = document(SalesDocumentType.ORDER, SalesDocumentStatus.DRAFT, List.of(item()));

        assertInvalidTransition(draft, SalesDocumentStatus.ISSUED);
        assertInvalidTransition(draft, SalesDocumentStatus.PAID);
    }

    @Test
    void orderCancellationAndInvoicingFollowTheMatrix() {
        assertThat(document(SalesDocumentType.ORDER, SalesDocumentStatus.DRAFT, List.of(item()))
                .transitionTo(SalesDocumentStatus.CANCELLED).status())
                .isEqualTo(SalesDocumentStatus.CANCELLED);
        assertThat(document(SalesDocumentType.ORDER, SalesDocumentStatus.CONFIRMED, List.of(item()))
                .transitionTo(SalesDocumentStatus.CANCELLED).status())
                .isEqualTo(SalesDocumentStatus.CANCELLED);
        assertThat(document(SalesDocumentType.ORDER, SalesDocumentStatus.CONFIRMED, List.of(item()))
                .transitionTo(SalesDocumentStatus.INVOICED).status())
                .isEqualTo(SalesDocumentStatus.INVOICED);
        assertThat(document(SalesDocumentType.ORDER, SalesDocumentStatus.CONFIRMED, List.of(item()))
                .transitionTo(SalesDocumentStatus.PARTIALLY_INVOICED).status())
                .isEqualTo(SalesDocumentStatus.PARTIALLY_INVOICED);
        assertThat(document(SalesDocumentType.ORDER, SalesDocumentStatus.PARTIALLY_INVOICED, List.of(item()))
                .transitionTo(SalesDocumentStatus.INVOICED).status())
                .isEqualTo(SalesDocumentStatus.INVOICED);
        assertThat(document(SalesDocumentType.ORDER, SalesDocumentStatus.PARTIALLY_INVOICED, List.of(item()))
                .transitionTo(SalesDocumentStatus.PARTIALLY_INVOICED).status())
                .isEqualTo(SalesDocumentStatus.PARTIALLY_INVOICED);

        assertInvalidTransition(
                document(SalesDocumentType.ORDER, SalesDocumentStatus.PARTIALLY_INVOICED, List.of(item())),
                SalesDocumentStatus.CANCELLED);
        assertInvalidTransition(
                document(SalesDocumentType.ORDER, SalesDocumentStatus.INVOICED, List.of(item())),
                SalesDocumentStatus.CANCELLED);
    }

    // ── QUOTE ─────────────────────────────────────────────────────────────

    @Test
    void quoteDraftToSentWithItemsSucceeds() {
        var draft = document(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT, List.of(item()));

        assertThat(draft.transitionTo(SalesDocumentStatus.SENT).status())
                .isEqualTo(SalesDocumentStatus.SENT);
    }

    @Test
    void quoteDraftToSentWithoutItemsFailsWithNoItems() {
        var draft = document(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT, List.of());

        assertThatThrownBy(() -> draft.transitionTo(SalesDocumentStatus.SENT))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("SD_NO_ITEMS");
                    assertThat(ex.getMessage())
                            .isEqualTo("La cotización debe tener al menos un ítem para ser enviada");
                });
    }

    @Test
    void quoteSentCanBeAcceptedRejectedOrExpired() {
        var sent = document(SalesDocumentType.QUOTE, SalesDocumentStatus.SENT, List.of(item()));

        assertThat(sent.transitionTo(SalesDocumentStatus.ACCEPTED).status())
                .isEqualTo(SalesDocumentStatus.ACCEPTED);
        assertThat(sent.transitionTo(SalesDocumentStatus.REJECTED).status())
                .isEqualTo(SalesDocumentStatus.REJECTED);
        assertThat(sent.transitionTo(SalesDocumentStatus.EXPIRED).status())
                .isEqualTo(SalesDocumentStatus.EXPIRED);
    }

    @Test
    void quoteDraftCannotJumpToAcceptedOrCancelled() {
        var draft = document(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT, List.of(item()));

        assertInvalidTransition(draft, SalesDocumentStatus.ACCEPTED);
        assertInvalidTransition(draft, SalesDocumentStatus.CANCELLED);
    }

    // ── INVOICE / CREDIT_NOTE / DEBIT_NOTE ────────────────────────────────

    @Test
    void invoiceDraftCanBeIssuedAndThenPaid() {
        var draft = document(SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT, List.of(item()));

        var issued = draft.transitionTo(SalesDocumentStatus.ISSUED);
        var paid = issued.transitionTo(SalesDocumentStatus.PAID);

        assertThat(issued.status()).isEqualTo(SalesDocumentStatus.ISSUED);
        assertThat(paid.status()).isEqualTo(SalesDocumentStatus.PAID);
        assertThat(paid.id()).isEqualTo(DOC_ID);
    }

    @Test
    void invoiceDraftCannotJumpStraightToPaid() {
        assertInvalidTransition(
                document(SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT, List.of(item())),
                SalesDocumentStatus.PAID);
    }

    @Test
    void invoiceIssuedCanBeCancelledButDraftCannot() {
        assertThat(document(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED, List.of(item()))
                .transitionTo(SalesDocumentStatus.CANCELLED).status())
                .isEqualTo(SalesDocumentStatus.CANCELLED);

        assertInvalidTransition(
                document(SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT, List.of(item())),
                SalesDocumentStatus.CANCELLED);
    }

    @Test
    void creditAndDebitNotesFollowTheInvoiceRules() {
        for (var type : List.of(SalesDocumentType.CREDIT_NOTE, SalesDocumentType.DEBIT_NOTE)) {
            var draft = document(type, SalesDocumentStatus.DRAFT, List.of(item()));
            var issued = draft.transitionTo(SalesDocumentStatus.ISSUED);
            var paid = issued.transitionTo(SalesDocumentStatus.PAID);

            assertThat(issued.status()).isEqualTo(SalesDocumentStatus.ISSUED);
            assertThat(paid.status()).isEqualTo(SalesDocumentStatus.PAID);
            assertInvalidTransition(draft, SalesDocumentStatus.PAID);
        }
    }

    // ── Shared invalid cases ──────────────────────────────────────────────

    @Test
    void nullTargetIsAnInvalidTransition() {
        var draft = document(SalesDocumentType.ORDER, SalesDocumentStatus.DRAFT, List.of(item()));

        assertInvalidTransition(draft, null);
    }

    @Test
    void invalidTransitionMessageNamesTypeAndBothStates() {
        var draft = document(SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT, List.of(item()));

        assertThatThrownBy(() -> draft.transitionTo(SalesDocumentStatus.PAID))
                .isInstanceOfSatisfying(InvalidStateTransitionException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("SD_INVALID_TRANSITION");
                    assertThat(ex.getMessage())
                            .isEqualTo("Transición inválida para INVOICE: DRAFT → PAID");
                });
    }

    private static void assertInvalidTransition(SalesDocument doc, SalesDocumentStatus target) {
        assertThatThrownBy(() -> doc.transitionTo(target))
                .isInstanceOfSatisfying(InvalidStateTransitionException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("SD_INVALID_TRANSITION"));
    }

    private static SaleItem item() {
        return new SaleItem(null, DOC_ID, PRODUCT_ID, new BigDecimal("1"),
                new BigDecimal("100.00"), "IVA_19", new BigDecimal("19"),
                new BigDecimal("19.00"), new BigDecimal("100.00"), 1, null);
    }

    private static SalesDocument document(SalesDocumentType type, SalesDocumentStatus status,
                                          List<SaleItem> items) {
        return new SalesDocument(
                DOC_ID, type, status, "ORD-TEST-0001",
                CLIENT_ID, WAREHOUSE_ID, null, null, null,
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("19.00"),
                new BigDecimal("119.00"), USER_ID, CREATED_AT, UPDATED_AT,
                items, LocalDate.now().plusDays(30), false, "Venta de mostrador",
                new BigDecimal("12.50"), new BigDecimal("10.00"), "AMOUNT",
                new BigDecimal("10.00"), USER_ID, "Cliente frecuente");
    }
}
