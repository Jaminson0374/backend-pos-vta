package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.ShiftStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shifts")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class ShiftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cash_register_id", nullable = false)
    private UUID cashRegisterId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "opening_time", nullable = false)
    private OffsetDateTime openingTime;

    @Column(name = "closing_time")
    private OffsetDateTime closingTime;

    @Column(name = "opening_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAmount;

    @Column(name = "closing_amount", precision = 19, scale = 2)
    private BigDecimal closingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftStatus status;

    @Column(name = "z_report_url", length = 500)
    private String zReportUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private OffsetDateTime updatedAt;
}
