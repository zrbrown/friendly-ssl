package net.eightlives.friendlyssl.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("controller")
@Tag("slow")
@ExtendWith(MockitoExtension.class)
@WebMvcTest(controllers = CertificateController.class)
class CertificateControllerTest {

    @Import(CertificateController.class)
    @SpringBootApplication
    static class TestApp {
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SSLCertificateCreateRenewService createRenewService;

    @DisplayName("Test order returns 200")
    @ParameterizedTest(name = "for {0}")
    @CsvSource(value = {"SUCCESS", "ALREADY_VALID"})
    @Execution(ExecutionMode.SAME_THREAD)
    void ok(String status) throws Exception {
        CertificateRenewal renewal = new CertificateRenewal(
                CertificateRenewalStatus.valueOf(status),
                Instant.ofEpochSecond(100000));
        when(createRenewService.createOrRenew()).thenReturn(renewal);

        mvc.perform(get("/friendly-ssl/certificate/order"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        objectMapper.writer().writeValueAsString(renewal),
                        result.getResponse().getContentAsString()
                ));
    }

    @DisplayName("Test order returns 500")
    @ParameterizedTest(name = "for {0}")
    @CsvSource(value = {"ERROR"})
    @Execution(ExecutionMode.SAME_THREAD)
    void error(String status) throws Exception {
        CertificateRenewal renewal = new CertificateRenewal(
                CertificateRenewalStatus.valueOf(status),
                Instant.ofEpochSecond(100000));
        when(createRenewService.createOrRenew()).thenReturn(renewal);

        mvc.perform(get("/friendly-ssl/certificate/order"))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsString().isBlank()
                ));
    }
}
