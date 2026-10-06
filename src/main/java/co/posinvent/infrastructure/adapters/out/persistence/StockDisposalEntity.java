package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_disposals")
@Getter
@Setter
public class StockDisposalEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Version @Column(name = "version", nullable = false) private Long version;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "batch_id") private UUID batchId;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "disposal_type", nullable = false, length = 30) private String disposalType;
    @Column(nullable = false, precision = 15, scale = 4) private BigDecimal quantity;
    @Column(name = "unit_cost", precision = 15, scale = 6) private BigDecimal unitCost;
    @Column(nullable = false, columnDefinition = "TEXT") private String reason;
    @Column(name = "created_by", length = 100) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
}
