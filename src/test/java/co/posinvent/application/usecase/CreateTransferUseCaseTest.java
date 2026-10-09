package co.posinvent.application.usecase;

import co.posinvent.application.dto.TransferItemRequest;
import co.posinvent.application.dto.TransferRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.StockTransfer;
import co.posinvent.domain.model.TransferStatus;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.StockTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTransferUseCaseTest {

    @Mock
    private StockTransferRepository transferRepo;

    @Mock
    private StockRepository stockRepo;

    private CreateTransferUseCase useCase;

    private static final UUID SOURCE_WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID TARGET_WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new CreateTransferUseCase(transferRepo, stockRepo);
    }

    @Test
    void execute_createsDraftTransferWhenSourceStockIsSufficient() {
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, SOURCE_WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("10")));
        doAnswer(inv -> inv.getArgument(0)).when(transferRepo).save(any(StockTransfer.class));

        var response = useCase.execute(new TransferRequest(
                SOURCE_WAREHOUSE_ID, TARGET_WAREHOUSE_ID, "Traslado a bodega destino",
                List.of(new TransferItemRequest(PRODUCT_ID, BATCH_ID, new BigDecimal("4")))));

        assertThat(response.status()).isEqualTo(TransferStatus.DRAFT.name());
        assertThat(response.sourceWarehouseId()).isEqualTo(SOURCE_WAREHOUSE_ID);
        assertThat(response.targetWarehouseId()).isEqualTo(TARGET_WAREHOUSE_ID);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.items().get(0).quantity()).isEqualByComparingTo("4");

        var captor = ArgumentCaptor.forClass(StockTransfer.class);
        verify(transferRepo).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(TransferStatus.DRAFT);
        assertThat(captor.getValue().items()).hasSize(1);
    }

    @Test
    void execute_rejectsWhenSourceStockDoesNotExist() {
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, SOURCE_WAREHOUSE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new TransferRequest(
                SOURCE_WAREHOUSE_ID, TARGET_WAREHOUSE_ID, null,
                List.of(new TransferItemRequest(PRODUCT_ID, BATCH_ID, BigDecimal.ONE)))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TRANSFER_NO_STOCK");

        verify(transferRepo, never()).save(any());
    }

    @Test
    void execute_rejectsWhenSourceStockIsInsufficient() {
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, SOURCE_WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("3")));

        assertThatThrownBy(() -> useCase.execute(new TransferRequest(
                SOURCE_WAREHOUSE_ID, TARGET_WAREHOUSE_ID, null,
                List.of(new TransferItemRequest(PRODUCT_ID, BATCH_ID, new BigDecimal("4"))))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TRANSFER_NO_STOCK");

        verify(transferRepo, never()).save(any());
    }

    private InventoryStock stock(String quantity) {
        return new InventoryStock(
                UUID.randomUUID(), PRODUCT_ID, BATCH_ID, SOURCE_WAREHOUSE_ID,
                new BigDecimal(quantity), BigDecimal.ZERO, BigDecimal.TEN,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().minusHours(1));
    }
}
