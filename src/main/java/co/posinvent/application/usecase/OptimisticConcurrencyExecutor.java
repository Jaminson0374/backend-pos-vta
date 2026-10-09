package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Bounded retry executor for stock-mutating units of work.
 *
 * <p>Re-runs the entire transactional unit of work in a fresh (REQUIRES_NEW)
 * transaction on optimistic-lock conflicts, instead of failing fast. Each
 * attempt re-reads the fresh entity state, re-applies the delta and re-saves.
 * On exhaustion, surfaces {@code CONCURRENT_MODIFICATION}.</p>
 */
@Component
public class OptimisticConcurrencyExecutor {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final TransactionTemplate transactionTemplate;

    /**
     * Retry budget on optimistic-lock conflicts. Configurable so stress tests can raise it; the
     * production default stays bounded. Manual instantiation (unit tests) keeps {@link #DEFAULT_MAX_ATTEMPTS}.
     */
    @Value("${app.inventory.concurrency.max-attempts:5}")
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    public OptimisticConcurrencyExecutor(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(Supplier<T> unitOfWork) {
        return execute(maxAttempts, unitOfWork);
    }

    public <T> T execute(int maxAttempts, Supplier<T> unitOfWork) {
        int attempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;

        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> unitOfWork.get());
            } catch (OptimisticLockingFailureException ex) {
                // OptimisticLockingFailureException is the superclass of
                // ObjectOptimisticLockingFailureException, the concrete type
                // Spring ORM raises on a @Version conflict at flush/commit.
                if (attempt >= attempts) {
                    throw new BusinessException("CONCURRENT_MODIFICATION",
                            "El registro fue modificado por otro usuario. Reintentá la operación.");
                }
            }
        }
    }
}
