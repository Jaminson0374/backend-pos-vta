package co.posinvent.infrastructure.adapters.out.dian.thefactory;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.domain.model.ElectronicInvoiceResponse;
import co.posinvent.domain.repository.ElectronicInvoiceProvider;
import co.posinvent.infrastructure.config.FactoryProviderProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Real DIAN provider backed by The Factory HKA sandbox SOAP service.
 *
 * <p>Active only when {@code app.dian.provider=thefactory}; the mock remains the default and
 * Siigo remains available behind {@code app.dian.provider=siigo}. The adapter owns only
 * orchestration: XML translation lives in {@link TheFactoryInvoiceMapper} and transport in
 * {@link TheFactoryClient}.</p>
 */
@Service
@ConditionalOnProperty(name = "app.dian.provider", havingValue = "thefactory")
class TheFactoryProviderAdapter implements ElectronicInvoiceProvider {

    static final String CONFIG_MISSING = "DIAN_HKA_CONFIG_MISSING";
    static final String PAYLOAD_INVALID = "DIAN_HKA_PAYLOAD_INVALID";

    private final FactoryProviderProperties properties;
    private final TheFactoryClient client;
    private final TheFactoryInvoiceMapper mapper;

    TheFactoryProviderAdapter(RestClient.Builder restClientBuilder,
                              FactoryProviderProperties properties) {
        this.properties = properties;
        this.client = new TheFactoryClient(restClientBuilder, properties);
        this.mapper = new TheFactoryInvoiceMapper(properties);
    }

    @Override
    public ElectronicInvoiceResponse sendInvoice(ElectronicInvoiceRequest request) {
        requireCredentials();
        return mapper.toResponse(client.enviar(mapper.toEnviarEnvelope(request)));
    }

    @Override
    public ElectronicInvoiceResponse checkStatus(String cufe) {
        if (cufe == null || cufe.isBlank()) {
            throw new BusinessException(PAYLOAD_INVALID,
                    "El CUFE/CUDE es obligatorio para consultar el estado en The Factory HKA.");
        }
        requireCredentials();
        return mapper.toStatusResponse(client.estadoDocumento(
                mapper.toEstadoDocumentoEnvelope(cufe)));
    }

    private void requireCredentials() {
        if (isBlank(properties.tokenEmpresa())) {
            throw new BusinessException(CONFIG_MISSING,
                    "Falta FACTORY_API_TOKEN (tokenEmpresa) para The Factory HKA.");
        }
        if (isBlank(properties.tokenPassword())) {
            throw new BusinessException(CONFIG_MISSING,
                    "Falta FACTORY_API_PASSWORD (tokenPassword) para The Factory HKA.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
