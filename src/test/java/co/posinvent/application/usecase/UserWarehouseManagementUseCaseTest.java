package co.posinvent.application.usecase;

import co.posinvent.application.dto.UserWarehouseResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Role;
import co.posinvent.domain.model.User;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.UserRepository;
import co.posinvent.domain.repository.UserWarehouseRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserWarehouseManagementUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID SECOND_WAREHOUSE_ID = UUID.randomUUID();

    @Mock private UserWarehouseRepository membershipRepo;
    @Mock private UserRepository userRepo;
    @Mock private WarehouseRepository warehouseRepo;

    private ManageUserWarehousesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageUserWarehousesUseCase(membershipRepo, userRepo, warehouseRepo);
    }

    @Test
    void assignAddsMembershipWhenUserAndWarehouseAreActive() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user(true)));
        when(warehouseRepo.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse(true)));

        var response = useCase.assign(USER_ID, WAREHOUSE_ID);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.warehouseId()).isEqualTo(WAREHOUSE_ID);
        verify(membershipRepo).assign(USER_ID, WAREHOUSE_ID);
    }

    @Test
    void assignIsIdempotentForAnExistingMembership() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user(true)));
        when(warehouseRepo.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse(true)));

        var first = useCase.assign(USER_ID, WAREHOUSE_ID);

        assertThatCode(() -> {
            var second = useCase.assign(USER_ID, WAREHOUSE_ID);
            assertThat(second).isEqualTo(first);
        }).doesNotThrowAnyException();

        verify(membershipRepo, times(2)).assign(USER_ID, WAREHOUSE_ID);
    }

    @Test
    void listReturnsEveryMembershipOfAnExistingUser() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user(true)));
        when(membershipRepo.findWarehouseIdsByUserId(USER_ID))
                .thenReturn(Set.of(WAREHOUSE_ID, SECOND_WAREHOUSE_ID));

        List<UserWarehouseResponse> memberships = useCase.list(USER_ID);

        assertThat(memberships).containsExactlyInAnyOrder(
                new UserWarehouseResponse(USER_ID, WAREHOUSE_ID),
                new UserWarehouseResponse(USER_ID, SECOND_WAREHOUSE_ID));
    }

    @Test
    void listRejectsAnUnknownUser() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.list(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario");

        verifyNoInteractions(membershipRepo);
    }

    @Test
    void removeIsIdempotent() {
        assertThatCode(() -> {
            useCase.remove(USER_ID, WAREHOUSE_ID);
            useCase.remove(USER_ID, WAREHOUSE_ID);
        }).doesNotThrowAnyException();

        verify(membershipRepo, times(2)).remove(USER_ID, WAREHOUSE_ID);
        verifyNoInteractions(userRepo, warehouseRepo);
    }

    @Test
    void assignRejectsAnUnknownUser() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.assign(USER_ID, WAREHOUSE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario");

        verify(membershipRepo, never()).assign(USER_ID, WAREHOUSE_ID);
        verifyNoInteractions(warehouseRepo);
    }

    @Test
    void assignRejectsAnInactiveUser() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> useCase.assign(USER_ID, WAREHOUSE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "USER_INACTIVE");

        verify(membershipRepo, never()).assign(USER_ID, WAREHOUSE_ID);
        verifyNoInteractions(warehouseRepo);
    }

    @Test
    void assignRejectsAnUnknownWarehouse() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user(true)));
        when(warehouseRepo.findById(WAREHOUSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.assign(USER_ID, WAREHOUSE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Bodega");

        verify(membershipRepo, never()).assign(USER_ID, WAREHOUSE_ID);
    }

    @Test
    void assignRejectsAnInactiveWarehouse() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user(true)));
        when(warehouseRepo.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse(false)));

        assertThatThrownBy(() -> useCase.assign(USER_ID, WAREHOUSE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "WAREHOUSE_INACTIVE");

        verify(membershipRepo, never()).assign(USER_ID, WAREHOUSE_ID);
    }

    private static User user(boolean active) {
        return new User(
                USER_ID, "warehouse-user", "Warehouse User", "warehouse-user@example.com",
                new Role(ROLE_ID, "ALMACENISTA", null), active, null, null, null, null);
    }

    private static Warehouse warehouse(boolean active) {
        return new Warehouse(
                WAREHOUSE_ID, "Bodega test", null, Warehouse.WarehouseType.GENERAL, active, null);
    }
}
