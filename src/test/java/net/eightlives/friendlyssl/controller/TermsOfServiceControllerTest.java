package net.eightlives.friendlyssl.controller;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.model.TermsOfServiceAgreeRequest;
import net.eightlives.friendlyssl.service.TermsOfServiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TermsOfServiceControllerTest {

    private static final String TERMS_LINK = "http://localhost:8000";
    private static final URI TERMS_URI = URI.create(TERMS_LINK);
    private static final TermsOfServiceAgreeRequest TERMS_REQUEST = new TermsOfServiceAgreeRequest(TERMS_LINK);

    private TermsOfServiceController controller;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private TermsOfServiceService termsOfServiceService;

    @BeforeEach
    void setUp() {
        controller = new TermsOfServiceController(config, termsOfServiceService);
    }

    @DisplayName("Test agreeing returns 400 for invalid URI")
    @Test
    void invalidUri() {
        ResponseEntity<String> response = controller.agreeToTermsOfService(
                new TermsOfServiceAgreeRequest("not a uri")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @DisplayName("Test agreeing returns 500 when writing link throws exception")
    @Test
    void exception() {
        doThrow(new SSLCertificateException(new RuntimeException()))
                .when(termsOfServiceService).writeTermsLink(TERMS_URI, true);

        ResponseEntity<String> response = controller.agreeToTermsOfService(TERMS_REQUEST);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(
                "Exception occurred while writing to terms of service file for terms link http://localhost:8000",
                response.getBody());
    }

    @DisplayName("Test agreeing returns 200 when successful")
    @Test
    void success() {
        ResponseEntity<String> response = controller.agreeToTermsOfService(TERMS_REQUEST);

        verify(termsOfServiceService, times(1))
                .writeTermsLink(TERMS_URI, true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
