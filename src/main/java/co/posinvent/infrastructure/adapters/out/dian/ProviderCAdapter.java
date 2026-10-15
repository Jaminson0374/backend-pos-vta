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
 * Mock implementation of the DIAN ElectronicInvoiceProvider interface.
 *
 * Since the real Provider C SDK may not be available, this generates a fake CUFE
 * (SHA-256 of timestamp + document number) and a placeholder QR code.
 *
 * TODO: Replace with real Provider C integration when the SDK is available:
 *       - Connect to the official DIAN Habilitación/Envío API
 *       - Use the real certificate (from digital_certificates table) for signing
 *       - Use the real resolution number + prefix for authorization
 *       - Implement real status checking endpoint
 */
@Service
@ConditionalOnProperty(name = "app.dian.provider", havingValue = "mock", matchIfMissing = true)
class ProviderCAdapter implements ElectronicInvoiceProvider {

    @Override
    public ElectronicInvoiceResponse sendInvoice(ElectronicInvoiceRequest request) {
        // TODO: Replace with real Provider C API call
        // 1. Sign the invoice XML with the company's digital certificate
        // 2. Send to DIAN's Envío endpoint
        // 3. Parse the response for CUFE, QR, and status

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
        // TODO: Replace with real DIAN status check
        // GET /api/dian/status/{cufe} from the real Provider C API

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
