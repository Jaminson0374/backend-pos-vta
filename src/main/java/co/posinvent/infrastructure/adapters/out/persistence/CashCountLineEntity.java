package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cash_count_lines")
@Getter
@Setter
public class CashCountLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_count_id", nullable = false)
    private CashCountEntity cashCount;

    @Column(name = "denomination_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal denominationValue;

    @Column(name = "denomination_type", nullable = false, length = 20)
    private String denominationType;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
}
