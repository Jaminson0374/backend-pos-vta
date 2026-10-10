package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "despostes")
@Getter
@Setter
public class DesposteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_batch_id", nullable = false)
    private UUID sourceBatchId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "input_weight", precision = 19, scale = 6)
    private BigDecimal inputWeight;

    @Column(name = "total_cuts_weight", precision = 19, scale = 6)
    private BigDecimal totalCutsWeight;

    @Column(name = "waste_weight", precision = 19, scale = 6)
    private BigDecimal wasteWeight;

    @Column(name = "shrink_weight", precision = 19, scale = 6)
    private BigDecimal shrinkWeight;

    @Column(precision = 19, scale = 6)
    private BigDecimal deviation;

    @Column(precision = 19, scale = 6)
    private BigDecimal tolerance;

    @Column(name = "within_tolerance")
    private Boolean withinTolerance;

    @Column(name = "yield_percentage", precision = 9, scale = 4)
    private BigDecimal yieldPercentage;

    @Column(name = "total_commercial_value", precision = 19, scale = 6)
    private BigDecimal totalCommercialValue;

    @Column(name = "total_allocated_cost", precision = 19, scale = 6)
    private BigDecimal totalAllocatedCost;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "desposte", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DesposteCutEntity> cuts = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
