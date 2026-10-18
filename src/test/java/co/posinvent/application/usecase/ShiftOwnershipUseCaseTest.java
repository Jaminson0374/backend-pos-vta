package co.posinvent.application.usecase;

import co.posinvent.application.dto.CashCountLineRequest;
import co.posinvent.application.dto.RegisterCashCountRequest;
import co.posinvent.domain.model.CashCount;
import co.posinvent.domain.model.Role;
import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.domain.model.User;
import co.posinvent.domain.repository.CashCountRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ShiftRepository;
import co.posinvent.domain.repository.UserRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Object-level shift ownership: the opener is recorded in {@code user_id}, an optional
 * {@code assigned_to} cashier becomes the effective owner, only the effective owner or an
 * ADMIN may operate the shift, and closing records {@code closed_by}.
 */
@ExtendWith(MockitoExtension.class)
class ShiftOwnershipUseCaseTest {

    private static final UUID SHIFT_ID = UUID.randomUUID();
    private static final UUID CASH_REGISTER_ID = UUID.randomUUID();
    private static final UUID OPENER_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_CASHIER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock private ShiftRepository shiftRepository;
    @Mock private UserRepository userRepository;
    @Mock private SalesDocumentRepository documentRepo;
    @Mock private CashCountRepository cashCountRepository;
    @Mock private CurrentActorProvider actorProvider;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final ShiftAccessGuard accessGuard = new ShiftAccessGuard();

    @Test
    void adminOpensShiftAssignedToAnotherCashier() {
        actAs(ADMIN_ID, "ADMIN");
        when(shiftRepository.findByCashRegisterIdAndStatus(CASH_REGISTER_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.empty());
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(cashier(OWNER_ID, true)));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = createShiftUseCase().execute(
                new ShiftRequest(CASH_REGISTER_ID, OWNER_ID, new BigDecimal("100000")));

        assertThat(response.userId()).isEqualTo(ADMIN_ID);
        assertThat(response.assignedTo()).isEqualTo(OWNER_ID);
        assertThat(response.closedBy()).isNull();
    }

    @Test
    void cashierCannotAssignAnotherUser() {
        actAs(OTHER_CASHIER_ID, "CAJERO");

        assertThatThrownBy(() -> createShiftUseCase().execute(
                new ShiftRequest(CASH_REGISTER_ID, OWNER_ID, BigDecimal.ZERO)))
                .isInstanceOf(AccessDeniedException.class);

        verify(shiftRepository, never()).save(any(Shift.class));
        verifyNoInteractions(userRepository);
    }

    @Test
    void effectiveOwnerClosesShiftAndClosedByIsRecorded() {
        actAs(OWNER_ID, "CAJERO");
        var shift = shift(OPENER_ID, OWNER_ID);
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));
        expectNoDocuments();

        var response = closeShiftUseCase().execute(SHIFT_ID, zeroCashCount());

        assertThat(response.closedBy()).isEqualTo(OWNER_ID);
        assertThat(response.assignedTo()).isEqualTo(OWNER_ID);
        assertThat(response.status()).isEqualTo(ShiftStatus.CLOSED);
    }

    @Test
    void anotherCashierCannotCloseTheShift() {
        actAs(OTHER_CASHIER_ID, "CAJERO");
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shift(OPENER_ID, OWNER_ID)));

        assertThatThrownBy(() -> closeShiftUseCase().execute(SHIFT_ID, zeroCashCount()))
                .isInstanceOf(AccessDeniedException.class);

        verify(shiftRepository, never()).save(any(Shift.class));
        verifyNoInteractions(documentRepo);
    }

    @Test
    void adminCanCloseSomeoneElsesShift() {
        actAs(ADMIN_ID, "ADMIN");
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shift(OPENER_ID, OWNER_ID)));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));
        expectNoDocuments();

        var response = closeShiftUseCase().execute(SHIFT_ID, zeroCashCount());

        assertThat(response.closedBy()).isEqualTo(ADMIN_ID);
        assertThat(response.assignedTo()).isEqualTo(OWNER_ID);
    }

    @Test
    void cashCountIsDeniedForAForeignCashier() {
        actAs(OTHER_CASHIER_ID, "CAJERO");
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shift(OPENER_ID, OWNER_ID)));

        assertThatThrownBy(() -> registerCashCountUseCase().execute(SHIFT_ID, cashCountRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(documentRepo);
        verify(cashCountRepository, never()).save(any(CashCount.class));
    }

    @Test
    void cashCountRecordsTheEffectiveOwnerAsCreator() {
        actAs(OWNER_ID, "CAJERO");
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shift(OPENER_ID, OWNER_ID)));
        when(cashCountRepository.save(any(CashCount.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        expectNoDocuments();

        registerCashCountUseCase().execute(SHIFT_ID, cashCountRequest());

        var captor = ArgumentCaptor.forClass(CashCount.class);
        verify(cashCountRepository).save(captor.capture());
        assertThat(captor.getValue().createdBy()).isEqualTo(OWNER_ID);
        assertThat(captor.getValue().shiftId()).isEqualTo(SHIFT_ID);
    }

    @Test
    void effectiveOwnerFallsBackToOpenerWhenNoAssignmentExists() {
        var shift = shift(OPENER_ID, null);

        assertThat(shift.effectiveOwnerId()).isEqualTo(OPENER_ID);
    }

    private CreateShiftUseCase createShiftUseCase() {
        return new CreateShiftUseCase(shiftRepository, actorProvider, userRepository);
    }

    private CloseShiftUseCase closeShiftUseCase() {
        return new CloseShiftUseCase(
                shiftRepository, documentRepo, eventPublisher, actorProvider, accessGuard);
    }

    private RegisterCashCountUseCase registerCashCountUseCase() {
        return new RegisterCashCountUseCase(
                shiftRepository, documentRepo, cashCountRepository, actorProvider, accessGuard);
    }

    private void actAs(UUID userId, String role) {
        lenient().when(actorProvider.current())
                .thenReturn(new CurrentActorProvider.CurrentActor(userId, role));
    }

    private void expectNoDocuments() {
        lenient().when(documentRepo.findByShiftIdAndType(any(), any())).thenReturn(List.of());
    }

    private static Shift shift(UUID openerId, UUID assignedTo) {
        return new Shift(
                SHIFT_ID, CASH_REGISTER_ID, openerId, assignedTo, null,
                OffsetDateTime.now().minusHours(8), null,
                BigDecimal.ZERO, null, ShiftStatus.OPEN, null,
                OffsetDateTime.now().minusHours(8));
    }

    private static User cashier(UUID id, boolean active) {
        return new User(
                id, "cashier", "Cashier", "cashier@example.com",
                new Role(UUID.randomUUID(), "CAJERO", null), active,
                null, null, null, null);
    }

    private static CashCountRequest zeroCashCount() {
        return new CashCountRequest(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    private static RegisterCashCountRequest cashCountRequest() {
        return new RegisterCashCountRequest(
                List.of(new CashCountLineRequest(new BigDecimal("1000.00"), "BILLETE", 1)),
                null);
    }
}
