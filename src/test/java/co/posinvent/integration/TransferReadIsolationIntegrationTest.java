package co.posinvent.integration;

import co.posinvent.application.dto.TransferResponse;
import co.posinvent.application.usecase.GetTransferUseCase;
import co.posinvent.application.usecase.ListTransfersUseCase;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-database proof that stock-transfer read isolation is applied in the SQL query through
 * warehouse membership: a restricted actor only sees transfers whose source or target warehouse
 * is one of theirs, and pagination totals count only those visible rows. ADMIN sees everything.
 */
class TransferReadIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ListTransfersUseCase listTransfersUseCase;
    @Autowired private GetTransferUseCase getTransferUseCase;

    private UUID memberA;
    private UUID memberB;
    private UUID warehouseA;
    private UUID warehouseB;
    private UUID warehouseC;
    private UUID transferAB;
    private UUID transferBA;
    private UUID transferAC;
    private UUID transferBC;

    @BeforeEach
    void seedTransfers() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            memberA = TestDataFactory.createUser(em, "transfer-member-a", "ALMACENISTA");
            memberB = TestDataFactory.createUser(em, "transfer-member-b", "ALMACENISTA");

            var suffix = UUID.randomUUID().toString().substring(0, 8);
            warehouseA = TestDataFactory.createWarehouse(
                    em, "TRANSFER-WH-A-" + suffix, Warehouse.WarehouseType.GENERAL);
            warehouseB = TestDataFactory.createWarehouse(
                    em, "TRANSFER-WH-B-" + suffix, Warehouse.WarehouseType.CORTES);
            warehouseC = TestDataFactory.createWarehouse(
                    em, "TRANSFER-WH-C-" + suffix, Warehouse.WarehouseType.VISCERAS);

            TestDataFactory.createUserWarehouse(em, memberA, warehouseA);
            TestDataFactory.createUserWarehouse(em, memberB, warehouseB);

            transferAB = TestDataFactory.createStockTransfer(
                    em, warehouseA, warehouseB, "DRAFT", memberA.toString());
            transferBA = TestDataFactory.createStockTransfer(
                    em, warehouseB, warehouseA, "DRAFT", memberB.toString());
            transferAC = TestDataFactory.createStockTransfer(
                    em, warehouseA, warehouseC, "DRAFT", memberA.toString());
            transferBC = TestDataFactory.createStockTransfer(
                    em, warehouseB, warehouseC, "DRAFT", memberB.toString());

            em.flush();
        });
    }

    @Test
    void memberSeesOnlyTransfersInvolvingTheirWarehouseAndPaginationStaysInSql() {
        asActor(memberA, "ALMACENISTA");

        Page<TransferResponse> page = listTransfersUseCase.execute(0, 20);

        assertThat(ids(page)).containsExactlyInAnyOrder(transferAB, transferBA, transferAC);
        assertThat(ids(page)).doesNotContain(transferBC);
        assertThat(page.getTotalElements()).isEqualTo(3);

        Page<TransferResponse> firstPage = listTransfersUseCase.execute(0, 1);
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
    }

    @Test
    void adminSeesEveryTransfer() {
        asActor(TEST_USER_ID, "ADMIN");

        Page<TransferResponse> page = listTransfersUseCase.execute(0, 50);

        assertThat(ids(page)).contains(transferAB, transferBA, transferAC, transferBC);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(4);
        assertThatCode(() -> getTransferUseCase.execute(transferBC)).doesNotThrowAnyException();
    }

    @Test
    void memberCanReadInvolvedTransferButNotAForeignOne() {
        asActor(memberA, "ALMACENISTA");

        assertThatCode(() -> getTransferUseCase.execute(transferAB)).doesNotThrowAnyException();
        assertThatCode(() -> getTransferUseCase.execute(transferBA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> getTransferUseCase.execute(transferBC))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tiene acceso a la bodega de este traslado.");
    }

    private static List<UUID> ids(Page<TransferResponse> page) {
        return page.getContent().stream().map(TransferResponse::id).toList();
    }

    private static void asActor(UUID userId, String role) {
        var principal = new PosUserDetails(
                userId, "user-" + role.toLowerCase(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), null, 0L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
