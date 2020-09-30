package net.eightlives.friendlyssl.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.eightlives.friendlyssl.model.TermsOfServiceAgreeRequest;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("controller")
@Tag("slow")
@ExtendWith(MockitoExtension.class)
@WebMvcTest(controllers = TermsOfServiceController.class)
@ActiveProfiles("test-tos-disabled")
class TermsOfServiceControllerDisabledTest {

    private static final String TERMS_LINK = "http://localhost:8000";
    private static final TermsOfServiceAgreeRequest TERMS_REQUEST = new TermsOfServiceAgreeRequest(TERMS_LINK);

    @SpringBootApplication
    static class TestApp {
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @DisplayName("Test TOS agree endpoint returns a 404")
    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    void notFound() throws Exception {
        mvc.perform(
                post("/friendly-ssl/tos/agree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TERMS_REQUEST)))
                .andExpect(status().isNotFound());
    }
}
