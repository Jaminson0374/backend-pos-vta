package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.AccountingPeriod;
import co.posinvent.domain.repository.AccountingPeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingPeriodUseCaseTest {

    private static final LocalDate DATE_IN_MARCH = LocalDate.of(2026, 3, 14);
    private static final String PERIOD = "2026-03";

    @Mock private AccountingPeriodRepository repo;

    private AccountingPeriodUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AccountingPeriodUseCase(repo);
        lenient().when(repo.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void close_createsAndClosesPeriodWhenRowIsAbsent() {
        UUID userId = UUID.randomUUID();
        when(repo.findById(PERIOD)).thenReturn(Optional.empty());

        AccountingPeriod closed = useCase.close(PERIOD, userId);

        assertThat(closed.period()).isEqualTo(PERIOD);
        assertThat(closed.status()).isEqualTo(AccountingPeriod.Status.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.closedBy()).isEqualTo(userId);
        assertThat(closed.openedAt()).isNotNull();
    }

    @Test
    void close_preservesOriginalOpenedAtWhenRowExists() {
        OffsetDateTime openedAt = OffsetDateTime.parse("2026-03-01T08:00:00Z");
        var existing = new AccountingPeriod(PERIOD, AccountingPeriod.Status.OPEN, openedAt, null, null);
        when(repo.findById(PERIOD)).thenReturn(Optional.of(existing));

        AccountingPeriod closed = useCase.close(PERIOD, null);

        assertThat(closed.openedAt()).isEqualTo(openedAt);
        assertThat(closed.status()).isEqualTo(AccountingPeriod.Status.CLOSED);
    }

    @Test
    void assertOpen_throwsWhenPeriodIsClosed() {
        var closed = new AccountingPeriod(PERIOD, AccountingPeriod.Status.CLOSED,
                OffsetDateTime.now(), OffsetDateTime.now(), UUID.randomUUID());
        when(repo.findById(PERIOD)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> useCase.assertOpen(DATE_IN_MARCH))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(PERIOD)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("ACCOUNTING_PERIOD_CLOSED");
    }

    @Test
    void assertOpen_allowsWhenPeriodRowIsAbsent() {
        when(repo.findById(PERIOD)).thenReturn(Optional.empty());

        assertThatCode(() -> useCase.assertOpen(DATE_IN_MARCH)).doesNotThrowAnyException();
    }

    @Test
    void assertOpen_allowsWhenPeriodIsOpen() {
        var open = new AccountingPeriod(PERIOD, AccountingPeriod.Status.OPEN,
                OffsetDateTime.now(), null, null);
        when(repo.findById(PERIOD)).thenReturn(Optional.of(open));

        assertThatCode(() -> useCase.assertOpen(DATE_IN_MARCH)).doesNotThrowAnyException();
    }

    @Test
    void list_returnsAllPeriods() {
        var open = new AccountingPeriod(PERIOD, AccountingPeriod.Status.OPEN,
                OffsetDateTime.now(), null, null);
        when(repo.findAll()).thenReturn(List.of(open));

        assertThat(useCase.list()).containsExactly(open);
    }
}
