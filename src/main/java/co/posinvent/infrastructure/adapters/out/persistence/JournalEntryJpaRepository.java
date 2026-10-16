package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface JournalEntryJpaRepository extends JpaRepository<JournalEntryEntity, UUID> {

    Optional<JournalEntryEntity> findBySourceTypeAndSourceId(String sourceType, UUID sourceId);

    @Query("SELECT j FROM JournalEntryEntity j WHERE " +
           "(:sourceType IS NULL OR j.sourceType = :sourceType) AND " +
           "(:from IS NULL OR j.entryDate >= :from) AND " +
           "(:to IS NULL OR j.entryDate <= :to) ORDER BY j.entryDate DESC, j.createdAt DESC")
    List<JournalEntryEntity> findFiltered(@Param("sourceType") String sourceType,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Query(value = """
        SELECT l.entry_id AS entryId, j.entry_number AS entryNumber, j.entry_date AS entryDate,
               j.description AS description, l.account_id AS accountId, l.debit, l.credit
        FROM journal_entry_lines l JOIN journal_entries j ON j.id = l.entry_id
        WHERE l.account_id = :accountId
          AND (:from IS NULL OR j.entry_date >= CAST(:from AS DATE))
          AND (:to IS NULL OR j.entry_date <= CAST(:to AS DATE))
        ORDER BY j.entry_date ASC, j.created_at ASC
        """, nativeQuery = true)
    List<Object[]> findLedgerRows(@Param("accountId") UUID accountId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    @Query(value = "SELECT nextval('journal_entry_number_seq')", nativeQuery = true)
    Long nextEntrySequence();

    @Query(value = """
        SELECT a.code AS accountCode, a.name AS accountName,
               COALESCE(SUM(l.debit), 0) AS totalDebit,
               COALESCE(SUM(l.credit), 0) AS totalCredit
        FROM puc_accounts a
        LEFT JOIN journal_entry_lines l ON l.account_id = a.id
        LEFT JOIN journal_entries j ON j.id = l.entry_id
        WHERE (:from IS NULL OR j.entry_date >= CAST(:from AS DATE))
          AND (:to IS NULL OR j.entry_date <= CAST(:to AS DATE))
        GROUP BY a.id, a.code, a.name, a.level
        HAVING COALESCE(SUM(l.debit), 0) + COALESCE(SUM(l.credit), 0) > 0
        ORDER BY a.code
        """, nativeQuery = true)
    List<Object[]> findTrialBalance(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
