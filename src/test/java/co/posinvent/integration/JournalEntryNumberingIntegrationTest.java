package co.posinvent.integration;

import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.JournalEntry;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.repository.PucAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for concurrency-safe journal-entry numbering.
 *
 * <p>Each invoice publishes an {@code InvoiceIssuedEvent} handled synchronously, so a burst of
 * concurrent checkouts drives {@link CreateJournalEntryUseCase#createAuto} from many transactions
 * at once. The old timestamp-derived number collided within the same millisecond and violated the
 * unique constraint on {@code journal_entries.entry_number}. The database sequence introduced in
 * {@code V116} removes the race: every entry must get a distinct number.</p>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: each worker thread owns its own
 * transaction so the row is committed (and the unique constraint enforced) before the run ends.</p>
 */
class JournalEntryNumberingIntegrationTest extends AbstractIntegrationTest {

    private static final int ENTRIES = 30;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Autowired private CreateJournalEntryUseCase journalUseCase;
    @Autowired private PucAccountRepository pucRepo;

    private UUID debitAccountId;
    private UUID creditAccountId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            debitAccountId = pucRepo.findByCode("1305").orElseThrow().id();
            creditAccountId = pucRepo.findByCode("4135").orElseThrow().id();
        });
    }

    @Test
    void concurrentJournalEntriesProduceDistinctNumbers() throws Exception {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        ExecutorService pool = Executors.newFixedThreadPool(ENTRIES);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<JournalEntry> created = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < ENTRIES; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    created.add(journalUseCase.createAuto(
                            "MANUAL", null, LocalDate.now(),
                            "Concurrent entry " + idx, balancedLines()));
                } catch (Throwable t) {
                    failures.add(t);
                }
                return null;
            }));
        }

        startGate.countDown();
        for (Future<?> future : futures) {
            future.get(3, TimeUnit.MINUTES);
        }
        pool.shutdown();

        // (a) No duplicate-key violation reaches any caller.
        assertThat(failures).isEmpty();
        assertThat(created).hasSize(ENTRIES);

        // (b) Every entry got a distinct number in the JE-{yyyyMMdd}-{seq} shape.
        List<String> numbers = created.stream().map(JournalEntry::entryNumber).toList();
        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers).allSatisfy(n -> assertThat(n).startsWith("JE-" + datePart + "-"));
    }

    private List<JournalEntryLine> balancedLines() {
        return List.of(
                new JournalEntryLine(null, null, debitAccountId, AMOUNT, BigDecimal.ZERO, "debit"),
                new JournalEntryLine(null, null, creditAccountId, BigDecimal.ZERO, AMOUNT, "credit"));
    }
}
