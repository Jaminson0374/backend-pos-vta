package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.StockTransfer;
import co.posinvent.domain.model.TransferStatus;
import co.posinvent.domain.repository.StockTransferRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelTransferUseCaseTest {

    @Mock
    private StockTransferRepository transferRepo;

    @Mock
    private CurrentActorProvider actorProvider;

    @Mock
    private WarehouseAccessGuard accessGuard;

    private CancelTransferUseCase useCase;

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID TRANSFER_ID = UUID.randomUUID();
    private static final UUID SOURCE_WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID TARGET_WAREHOUSE_ID = UUID.randomUUID();

    private CurrentActorProvider.CurrentActor actor;
    private WarehouseAccess access;

    @BeforeEach
    void setUp() {
        actor = new CurrentActorProvider.CurrentActor(ACTOR_ID, Set.of("ALMACENISTA"));
        access = new WarehouseAccess(false, Set.of(SOURCE_WAREHOUSE_ID));
        lenient().when(actorProvider.current()).thenReturn(actor);
        lenient().when(accessGuard.memberships(actor)).thenReturn(access);
        useCase = new CancelTransferUseCase(transferRepo, actorProvider, accessGuard);
    }

    @Test
    void execute_cancelsDraftTransfer() {
        when(transferRepo.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer(TransferStatus.DRAFT)));
        doAnswer(inv -> inv.getArgument(0)).when(transferRepo).save(any(StockTransfer.class));

        var response = useCase.execute(TRANSFER_ID);

        assertThat(response.status()).isEqualTo(TransferStatus.CANCELLED.name());

        var captor = ArgumentCaptor.forClass(StockTransfer.class);
        verify(transferRepo).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(captor.getValue().id()).isEqualTo(TRANSFER_ID);
    }

    @Test
    void execute_rejectsNonDraftTransfer() {
        when(transferRepo.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer(TransferStatus.CONFIRMED)));

        assertThatThrownBy(() -> useCase.execute(TRANSFER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TRANSFER_NOT_DRAFT");

        verify(transferRepo, never()).save(any());
    }

    @Test
    void execute_deniesWhenActorIsNotMemberOfTheSourceWarehouse() {
        when(transferRepo.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer(TransferStatus.DRAFT)));
        doThrow(new AccessDeniedException("No tiene acceso a la bodega de este traslado."))
                .when(accessGuard).requireAny(
                        actor, access, List.of(SOURCE_WAREHOUSE_ID),
                        "CANCEL_TRANSFER", TRANSFER_ID);

        assertThatThrownBy(() -> useCase.execute(TRANSFER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(transferRepo, never()).save(any());
    }

    @Test
    void execute_throwsWhenTransferDoesNotExist() {
        when(transferRepo.findById(TRANSFER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(TRANSFER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Traslado");

        verify(transferRepo, never()).save(any());
    }

    private StockTransfer transfer(TransferStatus status) {
        return new StockTransfer(
                TRANSFER_ID, SOURCE_WAREHOUSE_ID, TARGET_WAREHOUSE_ID, status,
                "Traslado", "SYSTEM", OffsetDateTime.now().minusHours(1),
                null, null, List.of());
    }
}
