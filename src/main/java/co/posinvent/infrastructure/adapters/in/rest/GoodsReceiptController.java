package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.dto.GoodsReceiptResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.GoodsReceiptRepository;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goods-receipts")
public class GoodsReceiptController {

    private final CreateGoodsReceiptUseCase createGoodsReceiptUseCase;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final BatchRepository batchRepository;

    public GoodsReceiptController(
            CreateGoodsReceiptUseCase createGoodsReceiptUseCase,
            GoodsReceiptRepository goodsReceiptRepository,
            BatchRepository batchRepository
    ) {
        this.createGoodsReceiptUseCase = createGoodsReceiptUseCase;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.batchRepository = batchRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public ResponseEntity<GoodsReceiptResponse> create(
            @Valid @RequestBody GoodsReceiptRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createGoodsReceiptUseCase.process(request, principal.userId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<GoodsReceiptResponse>> list(
            @RequestParam(required = false) UUID ocId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receiptDate"));

        var result = ocId != null
                ? goodsReceiptRepository.findByOcId(ocId, pageable)
                : goodsReceiptRepository.findAll(pageable);

        return ResponseEntity.ok(PageResponse.from(
                result,
                r -> GoodsReceiptResponse.from(r, r.batchIds(), List.of(), List.of())
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<GoodsReceiptResponse> getById(@PathVariable UUID id) {
        var receipt = goodsReceiptRepository.findById(id)
                .orElseThrow(() -> new co.posinvent.domain.exception.ResourceNotFoundException(
                        "Recepción", id));

        // Resolve batch IDs and expiration dates from the source_receipt_id foreign key
        var batches = batchRepository.findBySourceReceiptId(id);
        var batchIds = batches.stream()
                .map(co.posinvent.domain.model.Batch::id)
                .toList();
        var expirationDates = batches.stream()
                .map(co.posinvent.domain.model.Batch::expirationDate)
                .toList();

        return ResponseEntity.ok(GoodsReceiptResponse.from(receipt, batchIds, expirationDates, List.of()));
    }
}
