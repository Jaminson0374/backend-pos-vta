package co.posinvent.infrastructure.adapters.out.dian;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.domain.model.ElectronicInvoiceResponse;
import co.posinvent.domain.repository.ElectronicInvoiceProvider;
import co.posinvent.infrastructure.config.DianSiigoProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Real DIAN provider backed by the Siigo HTTP API.
 *
 * <p>Active only when {@code app.dian.provider=siigo}; otherwise {@code ProviderCAdapter}
 * (the mock) is used.</p>
 *
 * <h2>Siigo field mapping</h2>
 * <p>All request/response translation is centralized in the nested {@link SiigoInvoiceMapper}.
 * Siigo's public schema may evolve, so adjust the {@code @JsonProperty} names and the lookup
 * keys there — never spread Siigo-specific field names across this class. The success status
 * mapping is intentionally conservative: only Siigo "accepted" states become
 * {@code ACCEPTED_BY_DIAN}; anything unknown becomes {@code SENT} so a fake success is never
 * reported.</p>
 *
 * <p>Credentials are never logged.</p>
 */
@Service
@ConditionalOnProperty(name = "app.dian.provider", havingValue = "siigo")
class SiigoProviderAdapter implements ElectronicInvoiceProvider {

    static final String ERROR_CODE = "DIAN_PROVIDER_ERROR";

    private static final String PARTNER_ID_HEADER = "Partner-Id";
    private static final String SUCCESS_STATUS = "ACCEPTED_BY_DIAN";
    private static final String REJECTED_STATUS = "REJECTED_BY_DIAN";
    private static final String SENT_STATUS = "SENT";

    /** Refresh the token this long before its declared expiry to avoid racing the server. */
    private static final long TOKEN_EXPIRY_MARGIN_MS = 30_000L;
    private static final long DEFAULT_TOKEN_TTL_SECONDS = 3600L;

    private final RestClient.Builder restClientBuilder;
    private final DianSiigoProperties properties;

    /**
     * Built lazily from {@link #restClientBuilder} on first use. Lazy construction lets tests
     * bind a {@code MockRestServiceServer} request factory to the builder between adapter
     * construction and the first request. Thread-safe via double-checked locking.
     */
    private volatile RestClient restClient;

    private final Object tokenLock = new Object();
    private String accessToken;
    private long accessTokenExpiresAtMillis;

    SiigoProviderAdapter(RestClient.Builder restClientBuilder, DianSiigoProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
        applyTimeouts(restClientBuilder, properties);
    }

    @Override
    public ElectronicInvoiceResponse sendInvoice(ElectronicInvoiceRequest request) {
        try {
            var token = bearerToken();
            Map<?, ?> raw = restClient().post()
                    .uri(properties.baseUrl() + properties.invoicePath())
                    .header(PARTNER_ID_HEADER, properties.partnerId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(SiigoInvoiceMapper.toInvoiceRequest(request))
                    .retrieve()
                    .body(Map.class);
            return SiigoInvoiceMapper.toResponse(raw);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw providerError(e);
        }
    }

    @Override
    public ElectronicInvoiceResponse checkStatus(String cufe) {
        try {
            var token = bearerToken();
            var cufeParam = cufe == null ? "" : URLEncoder.encode(cufe, StandardCharsets.UTF_8);
            var uri = properties.baseUrl() + properties.statusPath() + "?cufe=" + cufeParam;
            Map<?, ?> raw = restClient().get()
                    .uri(uri)
                    .header(PARTNER_ID_HEADER, properties.partnerId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
            return SiigoInvoiceMapper.toResponse(raw);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw providerError(e);
        }
    }

    /**
     * Returns a valid bearer token, refreshing it when close to expiry. Synchronized so a
     * burst of concurrent sends performs a single authentication.
     */
    private String bearerToken() {
        synchronized (tokenLock) {
            var now = System.currentTimeMillis();
            if (accessToken != null && now < accessTokenExpiresAtMillis - TOKEN_EXPIRY_MARGIN_MS) {
                return accessToken;
            }

            SiigoAuthResponse authResponse;
            try {
                authResponse = restClient().post()
                        .uri(properties.baseUrl() + properties.authPath())
                        .header(PARTNER_ID_HEADER, properties.partnerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new SiigoAuthRequest(properties.username(), properties.accessKey()))
                        .retrieve()
                        .body(SiigoAuthResponse.class);
            } catch (RestClientException e) {
                throw providerError(e);
            }

            if (authResponse == null || authResponse.accessToken() == null
                    || authResponse.accessToken().isBlank()) {
                throw new BusinessException(ERROR_CODE,
                        "Error al comunicarse con Siigo: autenticacion sin token");
            }

            var ttlSeconds = authResponse.expiresIn() != null && authResponse.expiresIn() > 0
                    ? authResponse.expiresIn()
                    : DEFAULT_TOKEN_TTL_SECONDS;
            accessToken = authResponse.accessToken();
            accessTokenExpiresAtMillis = now + ttlSeconds * 1000L;
            return accessToken;
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

    private static void applyTimeouts(RestClient.Builder builder, DianSiigoProperties props) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        builder.requestFactory(factory);
    }

    private static BusinessException providerError(RestClientException e) {
        var detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return new BusinessException(ERROR_CODE, "Error al comunicarse con Siigo: " + detail);
    }

    // ---------------------------------------------------------------------------------------
    // Siigo request/response mapping — the only place Siigo field names live.
    // ---------------------------------------------------------------------------------------

    /**
     * Translates the domain request into the Siigo invoice body and translates Siigo responses
     * back into the domain response. Keep adjustments against the official Siigo docs here.
     *
     * <p>Discount mapping: the line discount is sent as {@code items[].discount} and the
     * document-level discount as {@code discount}. {@code items[].price} keeps the LIST unit
     * price, so the discount is carried separately and never baked into the price. Field
     * names are validated against the real Siigo documentation before going live.</p>
     */
    static final class SiigoInvoiceMapper {

        /** Siigo document-type id for a standard electronic invoice (adjust per account). */
        private static final int INVOICE_DOCUMENT_TYPE_ID = 1;

        private static final Set<String> SUCCESS_STATES = Set.of(
                "accepted", "aceptada", "aceptado", "accepted_by_dian",
                "valid", "valida", "validada", "aprobada");
        private static final Set<String> REJECTED_STATES = Set.of(
                "rejected", "rechazada", "rechazado", "error", "failed", "fallida");

        private SiigoInvoiceMapper() {}

        static SiigoInvoiceRequest toInvoiceRequest(ElectronicInvoiceRequest request) {
            var items = new ArrayList<SiigoItem>();
            if (request.items() != null) {
                for (var item : request.items()) {
                    items.add(toItem(item));
                }
            }
            var company = request.companyInfo();
            var customer = new SiigoCustomer(
                    request.clientNit(),
                    request.clientName(),
                    0);
            var document = new SiigoDocument(INVOICE_DOCUMENT_TYPE_ID, null);
            return new SiigoInvoiceRequest(
                    document,
                    request.issueDate(),
                    customer,
                    items,
                    request.totalAmount(),
                    request.discountTotal(),
                    company != null ? company.resolutionNumber() : null);
        }

        private static SiigoItem toItem(ElectronicInvoiceRequest.InvoiceItem item) {
            var taxes = new ArrayList<SiigoItemTax>();
            if (item.taxRate() != null && item.taxRate().signum() > 0) {
                taxes.add(new SiigoItemTax(null, item.taxRate()));
            }
            return new SiigoItem(
                    item.productCode(),
                    item.productName(),
                    item.quantity(),
                    item.unitPrice(),
                    item.discountAmount(),
                    taxes);
        }

        static ElectronicInvoiceResponse toResponse(Map<?, ?> raw) {
            if (raw == null) {
                throw new BusinessException(ERROR_CODE,
                        "Error al comunicarse con Siigo: respuesta vacia");
            }
            var cufe = firstNonBlank(
                    stringAt(raw, "cufe"),
                    stringAt(raw, "uuid"),
                    nestedStringAt(raw, "document", "cufe"),
                    nestedStringAt(raw, "document", "uuid"));
            var qr = firstNonBlank(
                    stringAt(raw, "qr"),
                    stringAt(raw, "qr_code"),
                    nestedStringAt(raw, "document", "qr"));
            var reference = firstNonBlank(
                    stringAt(raw, "id"),
                    nestedStringAt(raw, "document", "number"),
                    nestedStringAt(raw, "document", "id"));
            var status = mapStatus(firstNonBlank(
                    stringAt(raw, "status"),
                    stringAt(raw, "state"),
                    nestedStringAt(raw, "document", "status")));
            return new ElectronicInvoiceResponse(cufe, qr, status, reference);
        }

        private static String mapStatus(String rawStatus) {
            if (rawStatus == null || rawStatus.isBlank()) {
                return SENT_STATUS;
            }
            var normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
            if (SUCCESS_STATES.contains(normalized)) {
                return SUCCESS_STATUS;
            }
            if (REJECTED_STATES.contains(normalized)) {
                return REJECTED_STATUS;
            }
            return SENT_STATUS;
        }

        private static String stringAt(Map<?, ?> raw, String key) {
            var value = raw.get(key);
            return value != null ? String.valueOf(value) : null;
        }

        private static String nestedStringAt(Map<?, ?> raw, String parentKey, String key) {
            var parent = raw.get(parentKey);
            if (parent instanceof Map<?, ?> map) {
                var value = map.get(key);
                return value != null ? String.valueOf(value) : null;
            }
            return null;
        }

        private static String firstNonBlank(String... candidates) {
            for (var candidate : candidates) {
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return null;
        }
    }

    // DTOs private to the adapter. JSON names follow the Siigo API contract.
    private record SiigoAuthRequest(
            @JsonProperty("username") String username,
            @JsonProperty("access_key") String accessKey) {}

    private record SiigoAuthResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn) {}

    private record SiigoInvoiceRequest(
            @JsonProperty("document") SiigoDocument document,
            @JsonProperty("date") String date,
            @JsonProperty("customer") SiigoCustomer customer,
            @JsonProperty("items") List<SiigoItem> items,
            @JsonProperty("total") BigDecimal total,
            @JsonProperty("discount") BigDecimal discount,
            @JsonProperty("resolution_number") String resolutionNumber) {}

    private record SiigoDocument(
            @JsonProperty("id") Integer id,
            @JsonProperty("prefix") String prefix) {}

    private record SiigoCustomer(
            @JsonProperty("identification") String identification,
            @JsonProperty("name") String name,
            @JsonProperty("branch_office") int branchOffice) {}

    private record SiigoItem(
            @JsonProperty("code") String code,
            @JsonProperty("description") String description,
            @JsonProperty("quantity") BigDecimal quantity,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("discount") BigDecimal discount,
            @JsonProperty("taxes") List<SiigoItemTax> taxes) {}

    private record SiigoItemTax(
            @JsonProperty("id") Integer id,
            @JsonProperty("rate") BigDecimal rate) {}
}
