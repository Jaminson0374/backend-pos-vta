package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "company_config")
@Getter
@Setter
public class CompanyConfigEntity {

    @Id
    private Long id;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(nullable = false, length = 20)
    private String nit;

    @Column(length = 255)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "economic_activity", length = 255)
    private String economicActivity;

    @Column(name = "tax_regime", length = 100)
    private String taxRegime;

    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_warehouse_id")
    private WarehouseEntity mainWarehouse;

    @Column(name = "auto_generate_journal_entries", nullable = false)
    private boolean autoGenerateJournalEntries;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "moratory_interest_rate", precision = 5, scale = 2)
    private java.math.BigDecimal moratoryInterestRate;

    @Column(name = "interest_grace_days")
    private Integer interestGraceDays;

    @Column(name = "interest_compound_frequency", length = 20)
    private String interestCompoundFrequency;

    @Column(name = "costing_method", length = 20)
    private String costingMethod;

    @Column(name = "overhead_allocation_base", length = 10)
    private String overheadAllocationBase;

    @Column(name = "overhead_rate", precision = 5, scale = 2)
    private java.math.BigDecimal overheadRate;

    @Column(name = "dian_resolution_id")
    private java.util.UUID dianResolutionId;

    @Column(name = "software_pin", length = 100)
    private String softwarePin;

    @Column(name = "certificate_id")
    private java.util.UUID certificateId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
