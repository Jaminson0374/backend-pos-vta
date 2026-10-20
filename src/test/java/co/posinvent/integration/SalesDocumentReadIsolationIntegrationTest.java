package co.posinvent.integration;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.SalesDocumentResponse;
import co.posinvent.application.usecase.ManageSalesDocumentUseCase;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-database proof that read isolation happens in the SQL query: a CAJERO only sees the
 * documents they created or that belong to a shift they effectively own, and pagination totals
 * count only visible rows. CONTADOR gets global read-only access.
 */
class SalesDocumentReadIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ManageSalesDocumentUseCase manageSalesDocumentUseCase;

    private UUID cashierA;
    private UUID cashierB;
    private UUID contador;
    private UUID docA;
    private UUID docB;
    private UUID docC;

    @BeforeEach
    void seedDocuments() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            cashierA = TestDataFactory.createUser(em, "cashier-a", "CAJERO");
            cashierB = TestDataFactory.createUser(em, "cashier-b", "CAJERO");
            contador = TestDataFactory.createUser(em, "contador", "CONTADOR");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "READ-ISO-" + UUID.randomUUID().toString().substring(0, 8), "Read Isolation Client");
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "READ-ISO-WH-" + UUID.randomUUID().toString().substring(0, 8),
                    Warehouse.WarehouseType.CORTES);

            var registerA = TestDataFactory.createCashRegister(em, "READ-ISO-A");
            var registerB = TestDataFactory.createCashRegister(em, "READ-ISO-B");
            var shiftA = TestDataFactory.createOpenShift(em, cashierA, registerA);
            // Assignee wins over the opener, exercising assigned_to.
            var shiftB = TestDataFactory.createOpenShift(em, TEST_USER_ID, cashierB, registerB);

            docA = TestDataFactory.createSalesDocument(
                    em, SalesDocumentType.ORDER, SalesDocumentStatus.CONFIRMED,
                    "READ-ISO-A-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, shiftA, cashierA);
            docB = TestDataFactory.createSalesDocument(
                    em, SalesDocumentType.ORDER, SalesDocumentStatus.CONFIRMED,
                    "READ-ISO-B-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, shiftB, cashierB);
            docC = TestDataFactory.createSalesDocument(
                    em, SalesDocumentType.ORDER, SalesDocumentStatus.DRAFT,
                    "READ-ISO-C-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, null, cashierA);

            em.flush();
        });
    }

    @Test
    void cashierSeesOnlyOwnDocumentsWhileContadorSeesEverything() {
        asActor(cashierA, "CAJERO");

        PageResponse<SalesDocumentResponse> firstPage =
                manageSalesDocumentUseCase.getDocuments(0, 20, null, null);
        assertThat(ids(firstPage)).containsExactlyInAnyOrder(docA, docC);
        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(2);

        // The total comes from the SQL count of visible rows, not from the fetched page.
        PageResponse<SalesDocumentResponse> paged =
                manageSalesDocumentUseCase.getDocuments(0, 1, null, null);
        assertThat(paged.content()).hasSize(1);
        assertThat(paged.totalElements()).isEqualTo(2);
        assertThat(paged.totalPages()).isEqualTo(2);

        assertThatCode(() -> manageSalesDocumentUseCase.getById(docA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> manageSalesDocumentUseCase.getById(docB))
                .isInstanceOf(AccessDeniedException.class);

        asActor(cashierB, "CAJERO");
        PageResponse<SalesDocumentResponse> cashierBPage =
                manageSalesDocumentUseCase.getDocuments(0, 20, null, null);
        assertThat(ids(cashierBPage)).containsExactly(docB);
        assertThatThrownBy(() -> manageSalesDocumentUseCase.getById(docA))
                .isInstanceOf(AccessDeniedException.class);

        asActor(contador, "CONTADOR");
        PageResponse<SalesDocumentResponse> contadorPage =
                manageSalesDocumentUseCase.getDocuments(0, 20, null, null);
        assertThat(ids(contadorPage)).contains(docA, docB, docC);
        assertThat(contadorPage.totalElements()).isGreaterThanOrEqualTo(3);
        assertThatCode(() -> manageSalesDocumentUseCase.getById(docA)).doesNotThrowAnyException();

        asActor(TEST_USER_ID, "ADMIN");
        assertThatCode(() -> manageSalesDocumentUseCase.getById(docB)).doesNotThrowAnyException();
    }

    private static List<UUID> ids(PageResponse<SalesDocumentResponse> page) {
        return page.content().stream().map(SalesDocumentResponse::id).toList();
    }

    private static void asActor(UUID userId, String role) {
        var principal = new PosUserDetails(
                userId, "user-" + role.toLowerCase(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), null, 0L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
