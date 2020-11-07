package net.eightlives.friendlyssl.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import net.eightlives.friendlyssl.service.PKCS12KeyStoreService;
import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("controller")
@Tag("slow")
@ExtendWith(MockitoExtension.class)
@WebMvcTest(controllers = CertificateController.class)
@ActiveProfiles({"test-base", "test-existing-keystore"})
class CertificateControllerTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FriendlySSLConfig config;
    @MockBean
    private SSLCertificateCreateRenewService createRenewService;
    @MockBean
    private PKCS12KeyStoreService keyStoreService;

    @BeforeEach
    void setUp() {
        when(config.getCertificateKeyAlias()).thenReturn("friendly-test");
    }

    @DisplayName("Test order returns 200")
    @ParameterizedTest(name = "for {0}")
    @ArgumentsSource(OkStatusAndCertificateProvider.class)
    @Execution(ExecutionMode.SAME_THREAD)
    void ok(CertificateRenewalStatus status, X509Certificate certificate) throws Exception {
        when(keyStoreService.getCertificate("friendly-test")).thenReturn(Optional.ofNullable(certificate));
        CertificateRenewal renewal = new CertificateRenewal(status, Instant.ofEpochSecond(100000));
        when(createRenewService.createOrRenew(certificate)).thenReturn(renewal);

        mvc.perform(get("/friendly-ssl/certificate/order"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        objectMapper.writer().writeValueAsString(renewal),
                        result.getResponse().getContentAsString()
                ));
    }

    static class OkStatusAndCertificateProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            X509Certificate certificate = mock(X509Certificate.class);

            return Stream.of(
                    Arguments.of(CertificateRenewalStatus.SUCCESS, certificate),
                    Arguments.of(CertificateRenewalStatus.SUCCESS, null),
                    Arguments.of(CertificateRenewalStatus.ALREADY_VALID, certificate),
                    Arguments.of(CertificateRenewalStatus.ALREADY_VALID, null)
            );
        }
    }

    @DisplayName("Test order returns 500")
    @ParameterizedTest(name = "for {0}")
    @ArgumentsSource(ErrorStatusAndCertificateProvider.class)
    @Execution(ExecutionMode.SAME_THREAD)
    void error(CertificateRenewalStatus status, X509Certificate certificate) throws Exception {
        when(keyStoreService.getCertificate("friendly-test")).thenReturn(Optional.ofNullable(certificate));
        CertificateRenewal renewal = new CertificateRenewal(status, Instant.ofEpochSecond(100000));
        when(createRenewService.createOrRenew(certificate)).thenReturn(renewal);

        mvc.perform(get("/friendly-ssl/certificate/order"))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsString().isBlank()
                ));
    }

    static class ErrorStatusAndCertificateProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            X509Certificate certificate = mock(X509Certificate.class);

            return Stream.of(
                    Arguments.of(CertificateRenewalStatus.ERROR, certificate),
                    Arguments.of(CertificateRenewalStatus.ERROR, null)
            );
        }
    }
}
