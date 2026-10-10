package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "desposte_cuts")
@Getter
@Setter
public class DesposteCutEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desposte_id", nullable = false)
    private DesposteEntity desposte;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "child_batch_id")
    private UUID childBatchId;

    @Column(precision = 19, scale = 6)
    private BigDecimal weight;

    @Column(name = "suggested_sale_price", precision = 19, scale = 6)
    private BigDecimal suggestedSalePrice;

    @Column(name = "commercial_value", precision = 19, scale = 6)
    private BigDecimal commercialValue;

    @Column(name = "allocated_cost", precision = 19, scale = 6)
    private BigDecimal allocatedCost;

    @Column(name = "unit_cost", precision = 19, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;
}
