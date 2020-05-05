package net.eightlives.friendlyssl.controller;

import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateControllerTest {

    private CertificateController controller;

    @Mock
    private SSLCertificateCreateRenewService createRenewService;

    @BeforeEach
    void setUp() {
        controller = new CertificateController(createRenewService);
    }

    @DisplayName("Test order returns 200")
    @ParameterizedTest(name = "for {0}")
    @CsvSource(value = {"SUCCESS", "ALREADY_VALID"})
    void ok(String status) {
        CertificateRenewal renewal = new CertificateRenewal(
                CertificateRenewalStatus.valueOf(status),
                Instant.ofEpochSecond(100000));
        when(createRenewService.createOrRenew()).thenReturn(renewal);

        ResponseEntity<CertificateRenewal> response = controller.order();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(renewal, response.getBody());
    }

    @DisplayName("Test order returns 500")
    @ParameterizedTest(name = "for {0}")
    @CsvSource(value = {"ERROR"})
    void error(String status) {
        CertificateRenewal renewal = new CertificateRenewal(
                CertificateRenewalStatus.valueOf(status),
                Instant.ofEpochSecond(100000));
        when(createRenewService.createOrRenew()).thenReturn(renewal);

        ResponseEntity<CertificateRenewal> response = controller.order();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getBody());
    }
}
