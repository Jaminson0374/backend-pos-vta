package co.posinvent.infrastructure.adapters.out.dian;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.infrastructure.config.DianSiigoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link SiigoProviderAdapter} using {@link MockRestServiceServer}. No real
 * network traffic is produced.
 *
 * <p>The adapter configures its request-timeouts on the injected {@link RestClient.Builder}
 * at construction time, so the test constructs the adapter first and binds the mock server
 * afterwards — the mock then wins when the client is lazily built on the first request.</p>
 */
class SiigoProviderAdapterTest {

    private static final String BASE_URL = "https://api.siigo.test";
    private static final String PARTNER_ID = "partner-test";
    private static final String USERNAME = "user-test";
    private static final String ACCESS_KEY = "key-test";
    private static final String ACCESS_TOKEN = "tok-123";

    private static final String AUTH_JSON = """
            {"access_token":"%s","expires_in":3600}
            """.formatted(ACCESS_TOKEN);

    private static final String INVOICE_JSON = """
            {"id":"SIIGO-1","cufe":"CUFE-ABC-123","qr":"https://qr.siigo.test/CUFE-ABC-123","status":"Accepted"}
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private SiigoProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        var properties = new DianSiigoProperties(
                USERNAME, ACCESS_KEY, PARTNER_ID,
                BASE_URL, "/auth", "/v1/invoices", "/v1/invoices/status",
                1000, 2000, 1L, 1L, true, false,
                0L, 5L, 13156L, 100L, 200L, 300L, 400L);
        adapter = new SiigoProviderAdapter(builder, properties, new ObjectMapper());
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void authenticatesThenMapsSiigoSuccessToAcceptedByDian() {
        expectAuthOnce();
        server.expect(requestTo(BASE_URL + "/v1/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Partner-Id", PARTNER_ID))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andRespond(withSuccess(INVOICE_JSON, MediaType.APPLICATION_JSON));

        var response = adapter.sendInvoice(sampleRequest());

        assertThat(response.status()).isEqualTo("ACCEPTED_BY_DIAN");
        assertThat(response.cufe()).isEqualTo("CUFE-ABC-123");
        assertThat(response.qrCode()).isEqualTo("https://qr.siigo.test/CUFE-ABC-123");
        assertThat(response.providerReference()).isEqualTo("SIIGO-1");
        server.verify();
    }

    @Test
    void reusesCachedTokenAcrossTwoSends() {
        expectAuthOnce();
        server.expect(ExpectedCount.twice(), requestTo(BASE_URL + "/v1/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(INVOICE_JSON, MediaType.APPLICATION_JSON));

        adapter.sendInvoice(sampleRequest());
        adapter.sendInvoice(sampleRequest());

        server.verify();
    }

    @Test
    void capturesStructured400ErrorsWithoutThrowing() {
        expectAuthOnce();
        server.expect(requestTo(BASE_URL + "/v1/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":400,"errors":[{"code":"invalid_postal_code",
                                "message":"Código postal inválido","params":["customer.address.postal_code"],
                                "detail":"Check the API documentation"}]}
                                """));

        var response = adapter.sendInvoice(sampleRequest());

        assertThat(response.status()).isEqualTo("REJECTED_BY_DIAN");
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).code()).isEqualTo("invalid_postal_code");
        assertThat(response.errors().get(0).message()).isEqualTo("Código postal inválido");
        assertThat(response.errors().get(0).params()).containsExactly("customer.address.postal_code");
    }

    @Test
    void mapsPaymentMeansAndTaxCatalogIds() {
        expectAuthOnce();
        server.expect(requestTo(BASE_URL + "/v1/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.payments[0].id").value(100))
                .andExpect(jsonPath("$.payments[0].value").value(1190.00))
                .andExpect(jsonPath("$.items[0].taxes[0].id").value(13156))
                .andExpect(jsonPath("$.customer.identification").value("900123456-7"))
                .andExpect(jsonPath("$.seller").value(1))
                .andRespond(withSuccess(INVOICE_JSON, MediaType.APPLICATION_JSON));

        adapter.sendInvoice(sampleRequest());

        server.verify();
    }

    @Test
    void httpErrorIsWrappedInProviderError() {
        expectAuthOnce();
        server.expect(requestTo(BASE_URL + "/v1/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        var thrown = catchThrowable(() -> adapter.sendInvoice(sampleRequest()));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("DIAN_PROVIDER_ERROR");
        assertThat(thrown).hasMessageContaining("Error al comunicarse con Siigo");
        server.verify();
    }

    @Test
    void sendsLineAndDocumentDiscountsInTheInvoiceBody() {
        expectAuthOnce();
        server.expect(requestTo(BASE_URL + "/v1/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.items[0].discount").value(100.00))
                // price stays the LIST unit price; the discount is never baked into it.
                .andExpect(jsonPath("$.items[0].price").value(1000.00))
                .andExpect(jsonPath("$.items[0].taxes[0].id").value(13156))
                .andRespond(withSuccess(INVOICE_JSON, MediaType.APPLICATION_JSON));

        adapter.sendInvoice(sampleRequest());

        server.verify();
    }

    private void expectAuthOnce() {
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/auth"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Partner-Id", PARTNER_ID))
                .andRespond(withSuccess(AUTH_JSON, MediaType.APPLICATION_JSON));
    }

    private static ElectronicInvoiceRequest sampleRequest() {
        return new ElectronicInvoiceRequest(
                "FV-1",
                "2026-10-11",
                "900123456-7",
                "Cliente Prueba",
                List.of(new ElectronicInvoiceRequest.InvoiceItem(
                        "P-1",
                        "Producto",
                        BigDecimal.ONE,
                        new BigDecimal("1000.00"),
                        "IVA",
                        new BigDecimal("19.00"),
                        new BigDecimal("190.00"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("100.00"))),
                Map.of("IVA", new BigDecimal("190.00")),
                new BigDecimal("1190.00"),
                new ElectronicInvoiceRequest.CompanyInfo(
                        "900987654-3",
                        "Empresa",
                        "Calle 1",
                        "3000000000",
                        "empresa@example.com",
                        "G",
                        "RESPONSABLE_IVA",
                        "RES-1",
                        "PIN-1"),
                new BigDecimal("100.00"),
                null,
                List.of(new ElectronicInvoiceRequest.Payment(
                        "CASH", "10", new BigDecimal("1190.00"), "2026-10-11")),
                "COP",
                "Venta de prueba");
    }
}
