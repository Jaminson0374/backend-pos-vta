package co.posinvent.infrastructure.adapters.out.dian;

import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.domain.model.ElectronicInvoiceResponse;
import co.posinvent.domain.repository.ElectronicInvoiceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Mock implementation of the DIAN {@link ElectronicInvoiceProvider} interface.
 *
 * <p>It is the default provider ({@code app.dian.provider=mock} or unset) and exists so the
 * POS can run end to end without fiscal credentials. It generates a deterministic-looking
 * fake CUFE (SHA-256 of document number, timestamp, and total), a placeholder QR payload,
 * and always reports {@code ACCEPTED_BY_DIAN}. It never contacts DIAN and never signs
 * anything, so its output has no fiscal validity.</p>
 *
 * <p>Real integrations live behind {@code app.dian.provider}: {@code siigo} uses the Siigo
 * REST API and {@code thefactory} uses The Factory HKA SOAP sandbox.</p>
 */
@Service
@ConditionalOnProperty(name = "app.dian.provider", havingValue = "mock", matchIfMissing = true)
class ProviderCAdapter implements ElectronicInvoiceProvider {

    @Override
    public ElectronicInvoiceResponse sendInvoice(ElectronicInvoiceRequest request) {
        var cufe = generateMockCufe(request);
        var qrCode = generateMockQr(request, cufe);

        return new ElectronicInvoiceResponse(
                cufe,
                qrCode,
                "ACCEPTED_BY_DIAN",
                "MOCK-" + System.currentTimeMillis()
        );
    }

    @Override
    public ElectronicInvoiceResponse checkStatus(String cufe) {
        return new ElectronicInvoiceResponse(
                cufe,
                null,
                "ACCEPTED_BY_DIAN",
                "MOCK-STATUS-" + System.currentTimeMillis()
        );
    }

    private String generateMockCufe(ElectronicInvoiceRequest request) {
        try {
            var raw = request.documentNumber() + "-" + System.currentTimeMillis() + "-" + request.totalAmount();
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toUpperCase().substring(0, 96);
        } catch (Exception e) {
            return "MOCK-CUFE-" + System.currentTimeMillis();
        }
    }

    private String generateMockQr(ElectronicInvoiceRequest request, String cufe) {
        var qrContent = String.format(
                "NIT=%s|NUM=%s|FEC=%s|VAL=%s|CUFE=%s",
                request.companyInfo() != null ? request.companyInfo().nit() : "MOCK",
                request.documentNumber(),
                request.issueDate(),
                request.totalAmount(),
                cufe
        );
        return Base64.getEncoder().encodeToString(qrContent.getBytes(StandardCharsets.UTF_8));
    }
}
