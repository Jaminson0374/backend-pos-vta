package co.posinvent.application.usecase;

import co.posinvent.application.dto.WarehouseRequest;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.model.Warehouse.WarehouseType;
import co.posinvent.domain.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
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
class WarehouseUseCaseTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    private WarehouseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new WarehouseUseCase(warehouseRepository);
    }

    @Test
    void create_persistsActiveWarehouseWithRequestedFields() {
        doAnswer(invocation -> invocation.getArgument(0, Warehouse.class))
                .when(warehouseRepository)
                .save(any(Warehouse.class));

        var request = new WarehouseRequest("Bodega Central", WarehouseType.CANAL, "Bogota");

        useCase.create(request);

        var captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.id()).isNull();
        assertThat(saved.name()).isEqualTo("Bodega Central");
        assertThat(saved.location()).isEqualTo("Bogota");
        assertThat(saved.warehouseType()).isEqualTo(WarehouseType.CANAL);
        assertThat(saved.active()).isTrue();
        assertThat(saved.createdAt()).isNull();
    }

    @Test
    void update_changesNameLocationAndTypeKeepingIdentityAndAudit() {
        var id = UUID.randomUUID();
        var createdAt = OffsetDateTime.now().minusDays(1);
        var existing = new Warehouse(id, "Vieja", "Cali", WarehouseType.CORTES, true, createdAt);

        when(warehouseRepository.findById(id)).thenReturn(Optional.of(existing));
        doAnswer(invocation -> invocation.getArgument(0, Warehouse.class))
                .when(warehouseRepository)
                .save(any(Warehouse.class));

        var request = new WarehouseRequest("Nueva", WarehouseType.EMBUTIDOS, "Medellin");

        useCase.update(id, request);

        var captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.name()).isEqualTo("Nueva");
        assertThat(saved.location()).isEqualTo("Medellin");
        assertThat(saved.warehouseType()).isEqualTo(WarehouseType.EMBUTIDOS);
        assertThat(saved.active()).isTrue();
        assertThat(saved.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void update_throwsWhenWarehouseDoesNotExist() {
        var id = UUID.randomUUID();
        when(warehouseRepository.findById(id)).thenReturn(Optional.empty());

        var request = new WarehouseRequest("Nueva", WarehouseType.GENERAL, null);

        assertThatThrownBy(() -> useCase.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(warehouseRepository, never()).save(any());
    }
}
