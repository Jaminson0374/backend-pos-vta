package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dian_resolutions")
@Getter
@Setter
public class DianResolutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resolution_number", nullable = false, length = 50)
    private String resolutionNumber;

    @Column(name = "resolution_date", nullable = false)
    private LocalDate resolutionDate;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(nullable = false, length = 10)
    private String prefix;

    @Column(name = "range_from", nullable = false)
    private Long rangeFrom;

    @Column(name = "range_to", nullable = false)
    private Long rangeTo;

    @Column(name = "current_number")
    private Long currentNumber;

    @Column(name = "software_pin", length = 100)
    private String softwarePin;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
