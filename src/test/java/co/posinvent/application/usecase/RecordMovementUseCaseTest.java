package co.posinvent.application.usecase;

import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.CurrentUserProvider;
import co.posinvent.domain.repository.KardexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordMovementUseCaseTest {

    @Mock
    private KardexRepository kardexRepo;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private RecordMovementUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordMovementUseCase(kardexRepo, currentUserProvider);
    }

    @Test
    void record_usesAuthenticatedUserIdAsCreatedBy() {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(userId));
        when(kardexRepo.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.record(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MovementType.ENTRY, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ONE,
                "PURCHASE", UUID.randomUUID(), "Ingreso de prueba");

        var captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(kardexRepo).save(captor.capture());
        assertThat(captor.getValue().createdBy()).isEqualTo(userId.toString());
    }

    @Test
    void record_fallsBackToSystemWhenNoAuthenticatedUser() {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.empty());
        when(kardexRepo.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.record(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MovementType.EXIT, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ZERO,
                "SALE", UUID.randomUUID(), "Salida de prueba");

        var captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(kardexRepo).save(captor.capture());
        assertThat(captor.getValue().createdBy()).isEqualTo("SYSTEM");
    }
}
