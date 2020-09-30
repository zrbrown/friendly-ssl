package net.eightlives.friendlyssl.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.model.TermsOfServiceAgreeRequest;
import net.eightlives.friendlyssl.service.TermsOfServiceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("controller")
@Tag("slow")
@ExtendWith(MockitoExtension.class)
@WebMvcTest(controllers = TermsOfServiceController.class)
@ActiveProfiles({"test-base", "test-existing-keystore"})
class TermsOfServiceControllerTest {

    private static final String TERMS_LINK = "http://localhost:8000";
    private static final URI TERMS_URI = URI.create(TERMS_LINK);
    private static final TermsOfServiceAgreeRequest TERMS_REQUEST = new TermsOfServiceAgreeRequest(TERMS_LINK);

    @SpringBootApplication
    static class TestApp {
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TermsOfServiceService termsOfServiceService;

    @DisplayName("Test agreeing returns 400 for invalid URI")
    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    void invalidUri() throws Exception {
        mvc.perform(
                post("/friendly-ssl/tos/agree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TermsOfServiceAgreeRequest("not a uri"))))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertEquals(
                        "URI could not be created from terms link 'not a uri'",
                        result.getResponse().getContentAsString()));
    }

    @DisplayName("Test agreeing returns 500 when writing link throws exception")
    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    void exception() throws Exception {
        doThrow(new SSLCertificateException(""))
                .when(termsOfServiceService).writeTermsLink(TERMS_URI, true);

        mvc.perform(
                post("/friendly-ssl/tos/agree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TERMS_REQUEST)))
                .andExpect(status().isInternalServerError())
                .andExpect(result -> assertEquals(
                        "Exception occurred while writing to terms of service file for terms link 'http://localhost:8000'",
                        result.getResponse().getContentAsString()));
    }

    @DisplayName("Test agreeing returns 200 when successful")
    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    void success() throws Exception {
        mvc.perform(
                post("/friendly-ssl/tos/agree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TERMS_REQUEST)))
                .andExpect(status().isOk());

        verify(termsOfServiceService, times(1)).writeTermsLink(TERMS_URI, true);
    }
}
