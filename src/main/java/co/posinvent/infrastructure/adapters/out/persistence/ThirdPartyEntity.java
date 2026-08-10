package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.ThirdParty.PersonType;
import co.posinvent.domain.model.ThirdParty.TaxRegime;
import co.posinvent.domain.model.ThirdParty.ThirdPartyType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "third_parties")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class ThirdPartyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "num_identification", nullable = false, unique = true, length = 40)
    private String numIdentification;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ThirdPartyType type;

    @Column(name = "price_list_id")
    private UUID priceListId;

    @Column(name = "credit_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 10)
    private PersonType personType;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", nullable = false, length = 20)
    private TaxRegime taxRegime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tax_responsibilities", nullable = false, columnDefinition = "jsonb")
    private List<String> taxResponsibilities;

    @Column(name = "city_code", length = 10)
    private String cityCode;

    @Column(name = "dian_classification", length = 10)
    private String dianClassification;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private OffsetDateTime updatedAt;

    // New fields
    @Column(name = "tp_category_id")
    private UUID tpCategoryId;

    @Column(name = "identification_type_id")
    private UUID identificationTypeId;

    @Column(length = 2)
    private String dv;

    @Column(name = "last_name", length = 150)
    private String lastName;

    @Column(name = "common_name", length = 200)
    private String commonName;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "city_id")
    private UUID cityId;

    @Column(length = 200)
    private String email;

    @Column(length = 255)
    private String website;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(name = "credit_days", nullable = false)
    private int creditDays;

    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_address", length = 255)
    private String contactAddress;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @Column(name = "tax_contact_first_name", length = 100)
    private String taxContactFirstName;

    @Column(name = "tax_contact_last_name", length = 100)
    private String taxContactLastName;

    @Column(name = "tax_email", length = 150)
    private String taxEmail;

    @Column(name = "billing_phone", length = 50)
    private String billingPhone;

    @Column(name = "is_gran_contribuyente", nullable = false)
    private boolean isGranContribuyente;

    @Column(name = "is_autoretenedor", nullable = false)
    private boolean isAutoretenedor;

    @Column(name = "is_agente_retencion_iva", nullable = false)
    private boolean isAgenteRetencionIva;

    @Column(name = "is_regimen_simple", nullable = false)
    private boolean isRegimenSimple;

    @Column(name = "other_tax_resp", nullable = false)
    private boolean otherTaxResp;

    @OneToOne(mappedBy = "thirdParty", fetch = FetchType.LAZY, optional = true)
    private EmployeeDetailsEntity employeeDetails;

    @Version
    private Integer version;

    @Column(name = "created_by")
    @CreatedBy
    private UUID createdBy;

    @Column(name = "updated_by")
    @LastModifiedBy
    private UUID updatedBy;
}
