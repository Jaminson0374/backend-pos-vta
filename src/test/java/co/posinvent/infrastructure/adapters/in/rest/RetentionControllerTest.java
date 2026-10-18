package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.SupplierInvoice;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import co.posinvent.infrastructure.config.JwtAuthFilter;
import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.infrastructure.config.JwtService;
import co.posinvent.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the retention aggregation endpoint (M1).
 *
 * <p>The frontend sends {@code period} as {@code YYYY-MM}; the filter must keep only the invoices
 * whose {@code issueDate} falls inside that month (inclusive) and must reject an unparseable period
 * with a clear 400 before touching the repositories.</p>
 */
@WebMvcTest(controllers = RetentionController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, RetentionControllerTest.JwtFilterTestConfig.class})
class RetentionControllerTest {

    private static final UUID SUPPLIER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierInvoiceRepository invoiceRepo;

    @MockitoBean
    private ThirdPartyRepository thirdPartyRepo;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @TestConfiguration
    static class JwtFilterTestConfig {
        @Bean
        JwtAuthFilter jwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService,
                                    JwtBlacklistService jwtBlacklistService) {
            return new JwtAuthFilter(jwtService, userDetailsService, jwtBlacklistService);
        }
    }

    private static RequestPostProcessor asRole(String role) {
        var principal = new PosUserDetails(
                UUID.randomUUID(),
                "user-" + role.toLowerCase(),
                "irrelevant-hash",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)),
                null,
                0L
        );
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private static SupplierInvoice invoice(LocalDate issueDate, String retention) {
        return new SupplierInvoice(
                UUID.randomUUID(), SUPPLIER_ID, "FAC-" + issueDate,
                issueDate, issueDate.plusDays(30),
                new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal(retention), new BigDecimal("1000.00"),
                InvoiceStatus.RECONCILED, null, UUID.randomUUID(), null, null, null,
                List.of());
    }

    private static ThirdParty supplier(String name) {
        return new ThirdParty(
                SUPPLIER_ID, "900123456", name, ThirdParty.ThirdPartyType.SUPPLIER,
                null, BigDecimal.ZERO,
                ThirdParty.PersonType.JURIDICA, ThirdParty.TaxRegime.ORDINARIO,
                List.of(), "001", null, true, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, 0, null, null, null, null, null, null, null,
                null, false, false, false, false, false, null);
    }

    private void givenAllInvoices(SupplierInvoice... invoices) {
        Page<SupplierInvoice> page = new PageImpl<>(List.of(invoices));
        given(invoiceRepo.findAll(any(Pageable.class))).willReturn(page);
        given(thirdPartyRepo.findById(SUPPLIER_ID))
                .willReturn(Optional.of(supplier("Proveedor Retenciones")));
    }

    @Test
    @DisplayName("period keeps only invoices issued inside that month")
    void periodKeepsOnlyInvoicesIssuedInsideThatMonth() throws Exception {
        givenAllInvoices(
                invoice(LocalDate.of(2026, 3, 15), "50.00"),
                invoice(LocalDate.of(2026, 4, 2), "70.00"));

        mockMvc.perform(get("/api/v1/retenciones")
                        .param("period", "2026-03")
                        .with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].supplierName").value("Proveedor Retenciones"))
                .andExpect(jsonPath("$.content[0].invoiceCount").value(1))
                .andExpect(jsonPath("$.content[0].retentionTotal").value(50.00));

        then(invoiceRepo).should().findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("period and supplierId are applied together")
    void periodAndSupplierFilterAreAppliedTogether() throws Exception {
        Page<SupplierInvoice> page = new PageImpl<>(List.of(
                invoice(LocalDate.of(2026, 3, 31), "50.00"),
                invoice(LocalDate.of(2026, 4, 30), "70.00")));
        given(invoiceRepo.findBySupplierId(eq(SUPPLIER_ID), any(Pageable.class))).willReturn(page);
        given(thirdPartyRepo.findById(SUPPLIER_ID))
                .willReturn(Optional.of(supplier("Proveedor Retenciones")));

        mockMvc.perform(get("/api/v1/retenciones")
                        .param("supplierId", SUPPLIER_ID.toString())
                        .param("period", "2026-04")
                        .with(asRole("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].invoiceCount").value(1))
                .andExpect(jsonPath("$.content[0].retentionTotal").value(70.00));

        then(invoiceRepo).should().findBySupplierId(eq(SUPPLIER_ID), any(Pageable.class));
        then(invoiceRepo).should(never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("without period the aggregation still covers every invoice")
    void absentPeriodKeepsAllInvoices() throws Exception {
        givenAllInvoices(
                invoice(LocalDate.of(2026, 3, 15), "50.00"),
                invoice(LocalDate.of(2026, 4, 2), "70.00"));

        mockMvc.perform(get("/api/v1/retenciones").with(asRole("AUXILIAR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].invoiceCount").value(2))
                .andExpect(jsonPath("$.content[0].retentionTotal").value(120.00));
    }

    @Test
    @DisplayName("an unparseable period is rejected with INVALID_PERIOD (400)")
    void invalidPeriodReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/retenciones")
                        .param("period", "2026-13")
                        .with(asRole("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PERIOD"));

        then(invoiceRepo).shouldHaveNoInteractions();
        then(thirdPartyRepo).shouldHaveNoInteractions();
    }
}
