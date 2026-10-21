package co.posinvent.application.usecase;

import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentWarehouseAccessUseCaseTest {

    @Mock private CurrentActorProvider actorProvider;
    @Mock private WarehouseAccessGuard accessGuard;

    @Test
    void returnsUnrestrictedAccessForAdmin() {
        var actor = new CurrentActorProvider.CurrentActor(UUID.randomUUID(), Set.of("ADMIN"));
        when(actorProvider.current()).thenReturn(actor);
        when(accessGuard.memberships(actor))
                .thenReturn(new WarehouseAccess(true, Set.of()));

        var response = new GetCurrentWarehouseAccessUseCase(actorProvider, accessGuard).execute();

        assertThat(response.unrestricted()).isTrue();
        assertThat(response.warehouseIds()).isEmpty();
    }

    @Test
    void returnsTheActorWarehouseSet() {
        var actor = new CurrentActorProvider.CurrentActor(UUID.randomUUID(), Set.of("ALMACENISTA"));
        var warehouseId = UUID.randomUUID();
        when(actorProvider.current()).thenReturn(actor);
        when(accessGuard.memberships(actor))
                .thenReturn(new WarehouseAccess(false, Set.of(warehouseId)));

        var response = new GetCurrentWarehouseAccessUseCase(actorProvider, accessGuard).execute();

        assertThat(response.unrestricted()).isFalse();
        assertThat(response.warehouseIds()).containsExactly(warehouseId);
    }
}
