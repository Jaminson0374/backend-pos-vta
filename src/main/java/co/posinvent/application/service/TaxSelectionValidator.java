package co.posinvent.application.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.FiscalResponsibility;
import co.posinvent.domain.model.TaxResponsibility;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TaxSelectionValidator {

    public void validateTaxResponsibilities(List<TaxResponsibility> catalog, List<String> selected) {
        Map<String, List<String>> excludes = catalog.stream()
                .collect(Collectors.toMap(TaxResponsibility::code, TaxResponsibility::excludes));
        validate(excludes, selected, "Régimen tributario");
    }

    public void validateFiscalResponsibilities(List<FiscalResponsibility> catalog, List<String> selected) {
        Map<String, List<String>> excludes = catalog.stream()
                .collect(Collectors.toMap(FiscalResponsibility::code, FiscalResponsibility::excludes));
        validate(excludes, selected, "Responsabilidades fiscales");
    }

    private void validate(Map<String, List<String>> excludesByCode, List<String> selected, String group) {
        if (selected == null || selected.isEmpty()) return;
        for (String code : selected) {
            List<String> excludes = excludesByCode.get(code);
            if (excludes == null) {
                throw new BusinessException("INVALID_TAX_SELECTION", group + ": opción desconocida '" + code + "'");
            }
            for (String other : selected) {
                if (!other.equals(code) && excludes.contains(other)) {
                    throw new BusinessException("INVALID_TAX_SELECTION",
                            group + ": combinación inválida ('" + code + "' + '" + other + "')");
                }
            }
        }
    }
}
