package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for {@link OptimisticConcurrencyExecutor}. No Spring context and
 * no Testcontainers — the {@link PlatformTransactionManager} is mocked so the
 * bounded retry loop can be exercised in isolation.
 */
@ExtendWith(MockitoExtension.class)
class OptimisticConcurrencyExecutorTest {

    @Mock
    private PlatformTransactionManager transactionManager;

    private OptimisticConcurrencyExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new OptimisticConcurrencyExecutor(transactionManager);
    }

    @Test
    void returnsSuccessValueOnFirstAttemptWithoutRetry() {
        @SuppressWarnings("unchecked")
        Supplier<String> unitOfWork = mock(Supplier.class);
        when(unitOfWork.get()).thenReturn("OK");

        String result = executor.execute(5, unitOfWork);

        assertThat(result).isEqualTo("OK");
        verify(unitOfWork, times(1)).get();
    }

    @Test
    void retriesAfterOptimisticLockFailureAndReturnsSuccessValue() {
        @SuppressWarnings("unchecked")
        Supplier<String> unitOfWork = mock(Supplier.class);
        when(unitOfWork.get())
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenReturn("RECOVERED");

        String result = executor.execute(5, unitOfWork);

        assertThat(result).isEqualTo("RECOVERED");
        verify(unitOfWork, times(2)).get();
    }

    @Test
    void throwsBusinessExceptionAfterMaxAttemptsWhenConflictsPersist() {
        @SuppressWarnings("unchecked")
        Supplier<String> unitOfWork = mock(Supplier.class);
        when(unitOfWork.get()).thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> executor.execute(3, unitOfWork))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CONCURRENT_MODIFICATION");

        verify(unitOfWork, times(3)).get();
    }

    @Test
    void defaultsToFiveAttemptsWhenMaxAttemptsIsNotPositive() {
        @SuppressWarnings("unchecked")
        Supplier<String> unitOfWork = mock(Supplier.class);
        when(unitOfWork.get()).thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> executor.execute(0, unitOfWork))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CONCURRENT_MODIFICATION");

        verify(unitOfWork, times(5)).get();
    }
}
