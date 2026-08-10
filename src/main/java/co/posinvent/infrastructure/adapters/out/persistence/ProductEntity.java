package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_code", nullable = false, unique = true, length = 50)
    private String productCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String barcode;

    @Column(length = 100)
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String description;

    // FK catalog references
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_type_id")
    private ProductTypeEntity productType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_state_id")
    private ProductStateEntity productState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private BrandEntity brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    private ProductModelEntity model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_category_id")
    private ProductCategoryEntity category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_group_id")
    private ProductGroupEntity productGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_of_measure_id")
    private UnitOfMeasureEntity unitOfMeasure;

    // Cost / price
    @Column(name = "cost_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal costPrice;

    @Column(name = "profit_margin", nullable = false, precision = 5, scale = 2)
    private BigDecimal profitMargin;

    @Column(name = "tax_type", nullable = false, length = 10)
    private String taxType;

    @Column(name = "sale_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal salePrice;

    // Inventory
    @Column(name = "costing_method", nullable = false, length = 30)
    private String costingMethod;

    @Column(name = "initial_stock", nullable = false, precision = 15, scale = 4)
    private BigDecimal initialStock;

    @Column(name = "min_stock", nullable = false, precision = 15, scale = 4)
    private BigDecimal minStock;

    @Column(name = "max_stock", nullable = false, precision = 15, scale = 4)
    private BigDecimal maxStock;

    @Column(name = "total_stock", nullable = false, precision = 15, scale = 4)
    private BigDecimal totalStock = BigDecimal.ZERO;

    @Column(name = "manufactured_in_house", nullable = false)
    private boolean manufacturedInHouse;

    @Column(name = "cost_affecting_exp", nullable = false)
    private boolean costAffectingExp;

    @Column(name = "manages_lots", nullable = false)
    private boolean manageLots;

    @Column(name = "is_perishable", nullable = false)
    private boolean perishable;

    @Column(name = "belongs_to_product", nullable = false)
    private boolean belongsToProduct;

    @Column(name = "sell_below_min", nullable = false)
    private boolean sellBelowMin;

    @Column(name = "is_inventoriable", nullable = false)
    private boolean inventoriable;

    // Specs
    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Column(columnDefinition = "TEXT")
    private String specifications;

    // Accounting template
    @Column(name = "accounting_template_id")
    private UUID accountingTemplateId;

    // Accounting FK references
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "income_account_id")
    private PucAccountEntity incomeAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_account_id")
    private PucAccountEntity inventoryAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_of_sales_acct_id")
    private PucAccountEntity costOfSalesAcct;

    // Concurrency
    @Version
    @Column(nullable = false)
    private Integer version;

    // Standard
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    @CreatedBy
    private UUID createdBy;

    @Column(name = "updated_by")
    @LastModifiedBy
    private UUID updatedBy;
}
