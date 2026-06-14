package co.posinvent.application.usecase;

import co.posinvent.application.dto.ProductGroupRequest;
import co.posinvent.application.dto.ProductGroupResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.ProductGroup;
import co.posinvent.domain.repository.ProductGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductGroupUseCase {

    private final ProductGroupRepository repository;

    public ProductGroupUseCase(ProductGroupRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProductGroupResponse> listAll() {
        return repository.findAllActive().stream().map(ProductGroupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductGroupResponse> listByCategory(UUID categoryId) {
        return repository.findByCategoryId(categoryId).stream().map(ProductGroupResponse::from).toList();
    }

    @Transactional
    public ProductGroupResponse create(ProductGroupRequest request) {
        if (repository.existsByName(request.name())) {
            throw new BusinessException("DUPLICATE_NAME", "Ya existe un grupo con ese nombre.");
        }
        var entity = new ProductGroup(null, request.name(), request.categoryId(), true, null, request.accountingTemplateId());
        return ProductGroupResponse.from(repository.save(entity));
    }
}
