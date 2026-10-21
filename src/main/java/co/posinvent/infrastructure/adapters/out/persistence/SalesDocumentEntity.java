package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales_documents")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class SalesDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesDocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SalesDocumentStatus status;

    @Column(name = "document_number", nullable = false, length = 50)
    private String documentNumber;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "cash_register_id")
    private UUID cashRegisterId;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(name = "total_net", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalNet;

    @Column(name = "total_tax_0", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalTax0;

    @Column(name = "total_tax_5", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalTax5;

    @Column(name = "total_tax_19", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalTax19;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_by", nullable = false)
    @CreatedBy
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    @LastModifiedBy
    private UUID updatedBy;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "is_credit_sale", nullable = false)
    private boolean isCreditSale;

    @Column(length = 500)
    private String reason;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "document_discount", nullable = false, precision = 19, scale = 2)
    private BigDecimal documentDiscount = BigDecimal.ZERO;

    @Column(name = "document_discount_type", nullable = false, length = 10)
    private String documentDiscountType = "NONE";

    @Column(name = "document_discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal documentDiscountValue = BigDecimal.ZERO;

    @Column(name = "discount_authorized_by")
    private UUID discountAuthorizedBy;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    @Column(name = "dian_cufe", length = 200)
    private String dianCufe;

    @Column(name = "dian_status", length = 30)
    private String dianStatus;

    @Column(name = "dian_provider_invoice_id", length = 100)
    private String dianProviderInvoiceId;

    @Column(name = "dian_pdf_url", columnDefinition = "TEXT")
    private String dianPdfUrl;

    @Column(name = "dian_xml_url", columnDefinition = "TEXT")
    private String dianXmlUrl;

    @Column(name = "dian_qr_url", columnDefinition = "TEXT")
    private String dianQrUrl;

    @Column(name = "dian_synced_at")
    private OffsetDateTime dianSyncedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<SaleItemEntity> items = new ArrayList<>();
}
