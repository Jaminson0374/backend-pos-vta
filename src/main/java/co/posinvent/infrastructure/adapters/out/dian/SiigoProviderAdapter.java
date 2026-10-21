package co.posinvent.infrastructure.adapters.out.dian;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.DianPaymentMeans;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.domain.model.ProviderError;
import co.posinvent.domain.model.ElectronicInvoiceResponse;
import co.posinvent.domain.repository.ElectronicInvoiceProvider;
import co.posinvent.infrastructure.config.DianSiigoProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
    private final ObjectMapper objectMapper;

    /**
     * Built lazily from {@link #restClientBuilder} on first use. Lazy construction lets tests
     * bind a {@code MockRestServiceServer} request factory to the builder between adapter
     * construction and the first request. Thread-safe via double-checked locking.
     */
    private volatile RestClient restClient;

    private final Object tokenLock = new Object();
    private String accessToken;
    private long accessTokenExpiresAtMillis;

    SiigoProviderAdapter(RestClient.Builder restClientBuilder, DianSiigoProperties properties,
                         ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
                    .body(SiigoInvoiceMapper.toInvoiceRequest(request, properties))
                    .retrieve()
                    .body(Map.class);
            return SiigoInvoiceMapper.toResponse(raw);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 422) {
                return SiigoInvoiceMapper.toErrorResponse(
                        e.getStatusCode().value(), e.getResponseBodyAsString(), objectMapper);
            }
            throw providerError(e);
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

        private static final Set<String> SUCCESS_STATES = Set.of(
                "accepted", "aceptada", "aceptado", "accepted_by_dian",
                "valid", "valida", "validada", "aprobada");
        private static final Set<String> REJECTED_STATES = Set.of(
                "rejected", "rechazada", "rechazado", "error", "failed", "fallida");

        private SiigoInvoiceMapper() {}

        static SiigoInvoiceRequest toInvoiceRequest(ElectronicInvoiceRequest request,
                                                     DianSiigoProperties properties) {
            var items = new ArrayList<SiigoItem>();
            if (request.items() != null) {
                for (var item : request.items()) {
                    items.add(toItem(item, properties));
                }
            }
            var customer = toCustomer(request);
            var document = new SiigoDocument(requiredDocumentType(properties));
            var currency = request.currency() == null || request.currency().isBlank()
                    ? null
                    : new SiigoCurrency(request.currency(), "1");
            return new SiigoInvoiceRequest(
                    document,
                    request.issueDate(),
                    customer,
                    requiredSeller(properties),
                    new SiigoStamp(properties.stampSend()),
                    new SiigoMail(properties.mailSend()),
                    request.observations(),
                    items,
                    toPayments(request, properties),
                    currency
            );
        }

        private static SiigoCustomer toCustomer(ElectronicInvoiceRequest request) {
            var client = request.client();
            if (client == null) {
                return new SiigoCustomer(
                        null, null, request.clientNit(), 0, null,
                        request.clientName() == null ? List.of() : List.of(request.clientName()),
                        null, List.of(), List.of());
            }
            var names = new ArrayList<String>();
            if (client.name() != null && !client.name().isBlank()) names.add(client.name());
            if (client.lastName() != null && !client.lastName().isBlank()) names.add(client.lastName());
            return new SiigoCustomer(
                    client.personType(),
                    client.identificationTypeCode(),
                    client.identification(),
                    0,
                    client.dv(),
                    names,
                    toAddress(client),
                    client.phone() == null || client.phone().isBlank()
                            ? List.of()
                            : List.of(new SiigoPhone("57", client.phone(), null)),
                    responsibilities(client.taxLevelCode())
            );
        }

        private static SiigoAddress toAddress(ElectronicInvoiceRequest.ClientInfo client) {
            if (client.address() == null && client.municipalityCode() == null) return null;
            return new SiigoAddress(
                    client.address(),
                    new SiigoCity(
                            client.countryCode() != null ? client.countryCode() : "CO",
                            client.countryCode() != null ? client.countryCode() : "CO",
                            client.departmentCode(),
                            client.departmentName(),
                            client.municipalityCode(),
                            client.cityName()
                    ),
                    client.postalCode()
            );
        }

        private static List<SiigoFiscalResponsibility> responsibilities(String taxLevelCode) {
            if (taxLevelCode == null || taxLevelCode.isBlank()) return List.of();
            var result = new ArrayList<SiigoFiscalResponsibility>();
            for (var code : taxLevelCode.split(";")) {
                if (!code.isBlank()) result.add(new SiigoFiscalResponsibility(code.trim()));
            }
            return result;
        }

        private static SiigoItem toItem(ElectronicInvoiceRequest.InvoiceItem item,
                                        DianSiigoProperties properties) {
            var taxes = new ArrayList<SiigoTax>();
            if (item.taxRate() != null
                    && (item.taxRate().signum() > 0
                    || (item.taxRate().signum() == 0 && properties.taxIdIva0() != null))) {
                taxes.add(new SiigoTax(taxId(item.taxRate(), properties)));
            }
            return new SiigoItem(
                    item.productCode(),
                    item.productName(),
                    item.quantity(),
                    item.unitPrice(),
                    item.discountAmount(),
                    taxes
            );
        }

        private static List<SiigoPayment> toPayments(ElectronicInvoiceRequest request,
                                                     DianSiigoProperties properties) {
            if (request.payments() == null || request.payments().isEmpty()) {
                throw new BusinessException("DIAN_PAYMENT_TOTAL_MISMATCH",
                        "Siigo exige al menos un medio de pago para la factura.");
            }
            var result = new ArrayList<SiigoPayment>();
            for (var payment : request.payments()) {
                result.add(new SiigoPayment(
                        paymentTypeId(payment.method(), properties),
                        payment.amount(),
                        payment.dueDate()
                ));
            }
            return result;
        }

        private static Long requiredDocumentType(DianSiigoProperties properties) {
            if (properties.documentTypeId() == null) {
                throw new BusinessException("DIAN_SIIGO_CONFIG_MISSING",
                        "Falta app.dian.siigo.document-type-id para el tipo de factura de Siigo.");
            }
            return properties.documentTypeId();
        }

        private static Long requiredSeller(DianSiigoProperties properties) {
            if (properties.sellerId() == null) {
                throw new BusinessException("DIAN_SIIGO_CONFIG_MISSING",
                        "Falta app.dian.siigo.seller-id para el vendedor de Siigo.");
            }
            return properties.sellerId();
        }

        private static Long taxId(BigDecimal rate, DianSiigoProperties properties) {
            if (rate.compareTo(BigDecimal.ZERO) == 0) return required(properties.taxIdIva0(), "tax-id-iva-0");
            if (rate.compareTo(new BigDecimal("5")) == 0) return required(properties.taxIdIva5(), "tax-id-iva-5");
            if (rate.compareTo(new BigDecimal("19")) == 0) return required(properties.taxIdIva19(), "tax-id-iva-19");
            throw new BusinessException("DIAN_UNSUPPORTED_TAX_RATE",
                    "Tarifa no soportada por el catálogo de Siigo: " + rate);
        }

        private static Long paymentTypeId(String method, DianSiigoProperties properties) {
            var means = DianPaymentMeans.fromInternal(method);
            return switch (means) {
                case CASH -> required(properties.paymentTypeCash(), "payment-type-cash");
                case CARD -> required(properties.paymentTypeCard(), "payment-type-card");
                case TRANSFER -> required(properties.paymentTypeTransfer(), "payment-type-transfer");
                case CHECK -> required(properties.paymentTypeCheck(), "payment-type-check");
            };
        }

        private static Long required(Long value, String property) {
            if (value == null) {
                throw new BusinessException("DIAN_SIIGO_CONFIG_MISSING",
                        "Falta app.dian.siigo." + property + " en la configuración.");
            }
            return value;
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
                    nestedStringAt(raw, "document", "uuid"),
                    nestedStringAt(raw, "stamp", "cufe"));
            var qr = firstNonBlank(
                    stringAt(raw, "qr"),
                    stringAt(raw, "qr_code"),
                    nestedStringAt(raw, "document", "qr"),
                    stringAt(raw, "public_url"));
            var pdf = firstNonBlank(
                    stringAt(raw, "pdf"),
                    stringAt(raw, "pdf_url"),
                    nestedStringAt(raw, "stamp", "pdf"));
            var xml = firstNonBlank(
                    stringAt(raw, "xml"),
                    stringAt(raw, "xml_url"),
                    nestedStringAt(raw, "stamp", "xml"));
            var reference = firstNonBlank(
                    stringAt(raw, "id"),
                    nestedStringAt(raw, "document", "number"),
                    nestedStringAt(raw, "document", "id"));
            var status = mapStatus(firstNonBlank(
                    stringAt(raw, "status"),
                    stringAt(raw, "state"),
                    nestedStringAt(raw, "stamp", "status"),
                    nestedStringAt(raw, "document", "status")));
            return new ElectronicInvoiceResponse(cufe, qr, status, reference, reference, pdf, xml, List.of());
        }

        static ElectronicInvoiceResponse toErrorResponse(int httpStatus, String body,
                                                          ObjectMapper mapper) {
            var errors = new ArrayList<ProviderError>();
            try {
                var root = mapper.readTree(body);
                var array = root.path("errors");
                if (!array.isArray()) array = root.path("error").path("errors");
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        var params = new ArrayList<String>();
                        var paramsNode = node.path("params");
                        if (paramsNode.isArray()) {
                            paramsNode.forEach(p -> params.add(p.asText()));
                        }
                        errors.add(new ProviderError(
                                text(node, "code"),
                                text(node, "message"),
                                params,
                                text(node, "detail")
                        ));
                    }
                }
            } catch (Exception ignored) {
                // Fall through to a generic HTTP error; the raw body is still not logged.
            }
            if (errors.isEmpty()) {
                errors.add(new ProviderError(
                        "DIAN_PROVIDER_HTTP_" + httpStatus,
                        "Siigo rechazó la factura sin un detalle estructurado.",
                        List.of(),
                        null
                ));
            }
            return new ElectronicInvoiceResponse(
                    null, null, "REJECTED_BY_DIAN", "HTTP-" + httpStatus,
                    null, null, null, errors);
        }

        private static String text(JsonNode node, String field) {
            var value = node.path(field);
            return value.isMissingNode() || value.isNull() ? null : value.asText();
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SiigoInvoiceRequest(
            @JsonProperty("document") SiigoDocument document,
            @JsonProperty("date") String date,
            @JsonProperty("customer") SiigoCustomer customer,
            @JsonProperty("seller") Long seller,
            @JsonProperty("stamp") SiigoStamp stamp,
            @JsonProperty("mail") SiigoMail mail,
            @JsonProperty("observations") String observations,
            @JsonProperty("items") List<SiigoItem> items,
            @JsonProperty("payments") List<SiigoPayment> payments,
            @JsonProperty("currency") SiigoCurrency currency) {}

    private record SiigoDocument(@JsonProperty("id") Long id) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SiigoCustomer(
            @JsonProperty("person_type") String personType,
            @JsonProperty("id_type") String idType,
            @JsonProperty("identification") String identification,
            @JsonProperty("branch_office") int branchOffice,
            @JsonProperty("check_digit") String checkDigit,
            @JsonProperty("name") List<String> name,
            @JsonProperty("address") SiigoAddress address,
            @JsonProperty("phones") List<SiigoPhone> phones,
            @JsonProperty("fiscal_responsibilities") List<SiigoFiscalResponsibility> fiscalResponsibilities) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SiigoAddress(
            @JsonProperty("address") String address,
            @JsonProperty("city") SiigoCity city,
            @JsonProperty("postal_code") String postalCode) {}

    private record SiigoCity(
            @JsonProperty("country_code") String countryCode,
            @JsonProperty("country_name") String countryName,
            @JsonProperty("state_code") String stateCode,
            @JsonProperty("state_name") String stateName,
            @JsonProperty("city_code") String cityCode,
            @JsonProperty("city_name") String cityName) {}

    private record SiigoPhone(
            @JsonProperty("indicative") String indicative,
            @JsonProperty("number") String number,
            @JsonProperty("extension") String extension) {}

    private record SiigoFiscalResponsibility(@JsonProperty("code") String code) {}

    private record SiigoCurrency(
            @JsonProperty("code") String code,
            @JsonProperty("exchange_rate") String exchangeRate) {}

    private record SiigoStamp(@JsonProperty("send") boolean send) {}

    private record SiigoMail(@JsonProperty("send") boolean send) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SiigoItem(
            @JsonProperty("code") String code,
            @JsonProperty("description") String description,
            @JsonProperty("quantity") BigDecimal quantity,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("discount") BigDecimal discount,
            @JsonProperty("taxes") List<SiigoTax> taxes) {}

    private record SiigoTax(@JsonProperty("id") Long id) {}

    private record SiigoPayment(
            @JsonProperty("id") Long id,
            @JsonProperty("value") BigDecimal value,
            @JsonProperty("due_date") String dueDate) {}
}
