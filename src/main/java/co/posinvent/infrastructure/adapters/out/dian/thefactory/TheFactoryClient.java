package co.posinvent.infrastructure.adapters.out.dian.thefactory;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.infrastructure.config.FactoryProviderProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * SOAP 1.1 transport for The Factory HKA sandbox.
 *
 * <p>The service is a BasicHttpBinding endpoint: every operation is a document-style POST with
 * {@code Content-Type: text/xml} and a quoted {@code SOAPAction} header. The client does not
 * parse the payload; it returns the raw XML so {@link TheFactoryInvoiceMapper} can translate
 * both {@code EnviarResult} and SOAP Faults. Credentials are never logged.</p>
 */
class TheFactoryClient {

    static final String ERROR_CODE = "DIAN_PROVIDER_ERROR";

    private static final String SOAP_ACTION_PREFIX = "http://tempuri.org/IService/";

    private final RestClient.Builder restClientBuilder;
    private final FactoryProviderProperties properties;

    private volatile RestClient restClient;

    TheFactoryClient(RestClient.Builder restClientBuilder, FactoryProviderProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
        applyTimeouts(restClientBuilder, properties);
    }

    String enviar(String envelope) {
        return post("Enviar", envelope);
    }

    String estadoDocumento(String envelope) {
        return post("EstadoDocumento", envelope);
    }

    private String post(String operation, String envelope) {
        try {
            return restClient().post()
                    .uri(properties.baseUrl())
                    .header("SOAPAction", "\"" + SOAP_ACTION_PREFIX + operation + "\"")
                    .contentType(MediaType.TEXT_XML)
                    .accept(MediaType.TEXT_XML, MediaType.APPLICATION_XML)
                    .body(envelope)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // A SOAP Fault arrives as HTTP 4xx/5xx with an XML body. Preserve it so the mapper
            // can expose the provider message instead of a generic transport error.
            var body = e.getResponseBodyAsString();
            if (body != null && body.contains("Envelope")) {
                return body;
            }
            throw providerError(e);
        } catch (RestClientException e) {
            throw providerError(e);
        }
    }

    private RestClient restClient() {
        var local = this.restClient;
        if (local == null) {
            synchronized (this) {
                local = this.restClient;
                if (local == null) {
                    local = restClientBuilder.build();
                    this.restClient = local;
                }
            }
        }
        return local;
    }

    private static void applyTimeouts(RestClient.Builder builder, FactoryProviderProperties props) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        builder.requestFactory(factory);
    }

    private static BusinessException providerError(RestClientException e) {
        var detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return new BusinessException(ERROR_CODE,
                "Error al comunicarse con The Factory HKA: " + detail);
    }
}
