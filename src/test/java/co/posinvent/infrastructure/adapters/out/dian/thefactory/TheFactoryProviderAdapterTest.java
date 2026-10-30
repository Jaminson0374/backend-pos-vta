package co.posinvent.infrastructure.adapters.out.dian.thefactory;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.infrastructure.config.FactoryProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link TheFactoryProviderAdapter}: conditional provider wiring, credential
 * fail-fast, and the two SOAP operations used by the invoice job.
 */
class TheFactoryProviderAdapterTest {

    private static final String BASE_URL = "https://hka.test/Service.svc";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private TheFactoryProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        adapter = new TheFactoryProviderAdapter(builder, props("token-empresa", "token-password"));
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void sendInvoicePostsEnviarAndMapsTheAcceptedResponse() {
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("SOAPAction", "\"http://tempuri.org/IService/Enviar\""))
                .andExpect(content().string(containsString("<Enviar")))
                .andExpect(content().string(containsString("<tokenEmpresa>token-empresa</tokenEmpresa>")))
                .andRespond(withSuccess(successXml(), MediaType.TEXT_XML));

        var response = adapter.sendInvoice(sampleRequest());

        assertThat(response.status()).isEqualTo("ACCEPTED_BY_DIAN");
        assertThat(response.cufe()).isEqualTo("CUFE-123");
        assertThat(response.providerReference()).isEqualTo("1");
        server.verify();
    }

    @Test
    void checkStatusPostsEstadoDocumentoWithTheCufe() {
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("SOAPAction", "\"http://tempuri.org/IService/EstadoDocumento\""))
                .andExpect(content().string(containsString("<documento>CUFE-123</documento>")))
                .andRespond(withSuccess(statusXml(), MediaType.TEXT_XML));

        var response = adapter.checkStatus("CUFE-123");

        assertThat(response.status()).isEqualTo("ACCEPTED_BY_DIAN");
        assertThat(response.cufe()).isEqualTo("CUFE-123");
        server.verify();
    }

    @Test
    void failsFastWhenTheSandboxTokensAreMissing() {
        var withoutTokens = new TheFactoryProviderAdapter(builder, props("", ""));

        var thrown = catchThrowable(() -> withoutTokens.sendInvoice(sampleRequest()));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("DIAN_HKA_CONFIG_MISSING");
        assertThat(thrown).hasMessageContaining("FACTORY_API_TOKEN");
        server.verify();
    }

    @Test
    void rejectsABlankCufeBeforeCallingTheProvider() {
        var thrown = catchThrowable(() -> adapter.checkStatus(" "));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("DIAN_HKA_PAYLOAD_INVALID");
        server.verify();
    }

    private static String successXml() {
        return """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <EnviarResponse xmlns="http://tempuri.org/">
                      <EnviarResult xmlns:a="http://schemas.datacontract.org/2004/07/ServiceSoap.UBL2._0.Response">
                        <a:codigo>0</a:codigo>
                        <a:consecutivoDocumento>1</a:consecutivoDocumento>
                        <a:cufe>CUFE-123</a:cufe>
                        <a:esValidoDian>true</a:esValidoDian>
                        <a:resultado>Aceptado</a:resultado>
                      </EnviarResult>
                    </EnviarResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
    }

    private static String statusXml() {
        return """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <EstadoDocumentoResponse xmlns="http://tempuri.org/">
                      <EstadoDocumentoResult xmlns:a="http://schemas.datacontract.org/2004/07/ServiceSoap.UBL2._0.Response">
                        <a:codigo>0</a:codigo>
                        <a:consecutivo>1</a:consecutivo>
                        <a:cufe>CUFE-123</a:cufe>
                        <a:esValidoDIAN>true</a:esValidoDIAN>
                        <a:resultado>Aceptado</a:resultado>
                      </EstadoDocumentoResult>
                    </EstadoDocumentoResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
    }

    private static ElectronicInvoiceRequest sampleRequest() {
        return new ElectronicInvoiceRequest(
                "1", "2026-10-29", "900123456", "ACME SAS",
                List.of(new ElectronicInvoiceRequest.InvoiceItem(
                        "P1", "Impresora", BigDecimal.ONE, new BigDecimal("100000"),
                        "IVA", new BigDecimal("19"), new BigDecimal("19000"),
                        new BigDecimal("100000"), BigDecimal.ZERO)),
                Map.of("19", new BigDecimal("19000")),
                new BigDecimal("119000"),
                new ElectronicInvoiceRequest.CompanyInfo(
                        "900987654", "POS INVENT SAS", "CL 1 2 3", "3001234567",
                        "emisor@posinvent.test", "4711", "ORDINARIO", "18760000001", "12345"),
                BigDecimal.ZERO,
                new ElectronicInvoiceRequest.ClientInfo(
                        "31", "900123456", "1", "Company", "ACME SAS", null,
                        "CL 10 20 30", "BOGOTA", "11001", "11", "Cundinamarca", "110111",
                        "CO", "O-13", "facturas@acme.test", "3009876543"),
                List.of(new ElectronicInvoiceRequest.Payment(
                        "CASH", "10", new BigDecimal("119000"), "2026-10-29")),
                "COP", null);
    }

    private static FactoryProviderProperties props(String tokenEmpresa, String tokenPassword) {
        return new FactoryProviderProperties(
                tokenEmpresa, tokenPassword,
                BASE_URL, true, 1000, 2000,
                "FV-1", "01", "05", "COP", "01", "04", "UN", "1", "00",
                "222222222222", "13", "Consumidor final");
    }
}
