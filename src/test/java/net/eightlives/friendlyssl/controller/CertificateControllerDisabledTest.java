package net.eightlives.friendlyssl.controller;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("controller")
@Tag("slow")
@ExtendWith(MockitoExtension.class)
@WebMvcTest(controllers = CertificateController.class)
@ActiveProfiles("test-certificate-disabled")
class CertificateControllerDisabledTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Autowired
    private MockMvc mvc;

    @DisplayName("Test endpoint returns a 404")
    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    void notFound() throws Exception {
        mvc.perform(get("/friendly-ssl/certificate/order"))
                .andExpect(status().isNotFound());
    }
}
