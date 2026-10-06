package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cost_layers")
@Getter
@Setter
public class CostLayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "remaining_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal remainingQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "entry_date", nullable = false)
    private OffsetDateTime entryDate;

    @Column(name = "source_movement_id")
    private UUID sourceMovementId;
}
