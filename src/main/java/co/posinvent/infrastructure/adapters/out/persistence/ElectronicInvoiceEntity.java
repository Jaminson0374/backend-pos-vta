package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.ElectronicInvoiceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "electronic_invoices")
@Getter
@Setter
public class ElectronicInvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_document_id", nullable = false, unique = true)
    private SalesDocumentEntity salesDocument;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private SalesDocumentEntity sourceDocument;

    @Column(unique = true, length = 200)
    private String cufe;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_response", columnDefinition = "jsonb")
    private String providerResponse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ElectronicInvoiceStatus status;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "response_at")
    private OffsetDateTime responseAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
