package co.posinvent.application.usecase;

import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.KardexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KardexQueryUseCaseTest {

    @Mock
    private KardexRepository kardexRepo;

    private KardexQueryUseCase useCase;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final OffsetDateTime FROM = OffsetDateTime.now().minusDays(7);
    private static final OffsetDateTime TO = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        useCase = new KardexQueryUseCase(kardexRepo);
    }

    @Test
    void search_parsesMovementTypeAndMapsResults() {
        var movement = movement();
        when(kardexRepo.search(eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID),
                eq(MovementType.ENTRY), eq(FROM), eq(TO), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(movement)));

        var page = useCase.search(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, "entry", FROM, TO, 0, 20);

        assertThat(page.getContent()).hasSize(1);
        var mapped = page.getContent().get(0);
        assertThat(mapped.id()).isEqualTo(movement.id());
        assertThat(mapped.productId()).isEqualTo(PRODUCT_ID);
        assertThat(mapped.movementType()).isEqualTo("ENTRY");
        assertThat(mapped.quantity()).isEqualByComparingTo("5");

        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(kardexRepo).search(eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID),
                eq(MovementType.ENTRY), eq(FROM), eq(TO), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 20));
    }

    @Test
    void search_treatsBlankMovementTypeAsNull() {
        when(kardexRepo.search(eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID),
                isNull(), eq(FROM), eq(TO), any(Pageable.class)))
                .thenReturn(Page.<InventoryMovement>empty());

        var page = useCase.search(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, "   ", FROM, TO, 1, 10);

        assertThat(page.getContent()).isEmpty();

        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(kardexRepo).search(eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID),
                isNull(), eq(FROM), eq(TO), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void search_treatsNullMovementTypeAsNull() {
        when(kardexRepo.search(eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID),
                isNull(), eq(FROM), eq(TO), any(Pageable.class)))
                .thenReturn(Page.<InventoryMovement>empty());

        var page = useCase.search(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, null, FROM, TO, 0, 5);

        assertThat(page.getContent()).isEmpty();
        verify(kardexRepo).search(eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID),
                isNull(), eq(FROM), eq(TO), any(Pageable.class));
    }

    @Test
    void search_rejectsUnknownMovementType() {
        assertThatThrownBy(() -> useCase.search(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, "NOT_A_TYPE", FROM, TO, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private InventoryMovement movement() {
        return new InventoryMovement(
                UUID.randomUUID(), PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, MovementType.ENTRY,
                new BigDecimal("5"), new BigDecimal("2"),
                BigDecimal.ZERO, new BigDecimal("5"),
                "MANUAL_ENTRY", null, "nota", "SYSTEM", OffsetDateTime.now());
    }
}
