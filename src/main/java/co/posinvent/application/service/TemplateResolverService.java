package co.posinvent.application.service;

import co.posinvent.domain.model.AccountingTemplate;
import co.posinvent.domain.repository.AccountingTemplateRepository;
import co.posinvent.domain.repository.ProductGroupRepository;
import co.posinvent.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class TemplateResolverService {

    private final ProductRepository productRepository;
    private final ProductGroupRepository productGroupRepository;
    private final AccountingTemplateRepository templateRepository;

    public TemplateResolverService(
            ProductRepository productRepository,
            ProductGroupRepository productGroupRepository,
            AccountingTemplateRepository templateRepository) {
        this.productRepository = productRepository;
        this.productGroupRepository = productGroupRepository;
        this.templateRepository = templateRepository;
    }

    /**
     * Resolves the accounting template for a given product and module.
     * Chain: product → productGroup → default for module.
     *
     * @param productId the product ID to resolve for
     * @param module    the module (e.g., "SALE", "PURCHASE")
     * @return the resolved template, or empty if nothing found
     */
    public Optional<AccountingTemplate> resolveForProduct(UUID productId, String module) {
        if (productId == null) {
            return resolveDefault(module);
        }

        // 1. Check product-level template
        var product = productRepository.findById(productId);
        if (product.isPresent() && product.get().accountingTemplateId() != null) {
            var template = templateRepository.findById(product.get().accountingTemplateId());
            if (template.isPresent()) {
                return template;
            }
        }

        // 2. Check product group-level template
        if (product.isPresent() && product.get().groupId() != null) {
            var group = productGroupRepository.findById(product.get().groupId());
            if (group.isPresent() && group.get().accountingTemplateId() != null) {
                var template = templateRepository.findById(group.get().accountingTemplateId());
                if (template.isPresent()) {
                    return template;
                }
            }
        }

        // 3. Fall back to default template for module
        return resolveDefault(module);
    }

    /**
     * Resolves the default template for a module when no product-specific template is available.
     */
    public Optional<AccountingTemplate> resolveDefault(String module) {
        return templateRepository.findDefaultByModule(module);
    }
}
