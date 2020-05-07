package net.eightlives.friendlyssl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.Metadata;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.exception.AcmeException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(MockitoExtension.class)
class TermsOfServiceServiceTest {

    private TermsOfServiceService service;

    @Mock
    private FriendlySSLConfig config;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TermsOfServiceService(config, mapper);
    }

    @DisplayName("Test getTermsOfServiceLink")
    @Nested
    class GetTermsOfServiceLink {

        @Mock
        private Session session;

        @DisplayName("When session.getMetadata() throws an exception")
        @Test
        void getMetadataThrowsException() throws AcmeException {
            when(session.getMetadata()).thenThrow(new AcmeException());

            assertThrows(SSLCertificateException.class, () -> service.getTermsOfServiceLink(session));
        }

        @DisplayName("When valid metadata")
        @Nested
        class ValidMetadata {

            @Mock
            private Metadata metadata;

            @BeforeEach
            void setUp() throws AcmeException {
                when(session.getMetadata()).thenReturn(metadata);
            }

            @DisplayName("When terms of service link is null")
            @Test
            void tosLinkNull() {
                when(metadata.getTermsOfService()).thenReturn(null);

                assertThrows(SSLCertificateException.class, () -> service.getTermsOfServiceLink(session));
            }

            @DisplayName("When terms of service link exists")
            @Test
            void tosLinkExists() {
                URI termsOfServiceLink = URI.create("http://localhost:8000");
                when(metadata.getTermsOfService()).thenReturn(termsOfServiceLink);

                assertEquals(termsOfServiceLink, service.getTermsOfServiceLink(session));
            }
        }
    }

    @DisplayName("Test termsAccepted")
    @Nested
    class TermsAccepted {

        private final URI termsOfServiceLink = URI.create("http://localhost:8000");

        @DisplayName("When file does not exist")
        @Test
        void fileDoesNotExist() {
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "notreal").toString()
            );

            assertFalse(service.termsAccepted(termsOfServiceLink));
        }

        @DisplayName("When file format is invalid")
        @Test
        void invalidFormat() {
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "tos_invalid.json").toString()
            );

            assertThrows(SSLCertificateException.class, () -> service.termsAccepted(termsOfServiceLink));
        }

        @DisplayName("When link is not found")
        @Test
        void linkNotFound() {
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "tos_no_match.json").toString()
            );

            assertFalse(service.termsAccepted(termsOfServiceLink));
        }

        @DisplayName("When file is empty")
        @Test
        void fileEmpty() {
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "tos_unaccepted.json").toString()
            );

            assertFalse(service.termsAccepted(termsOfServiceLink));
        }

        @DisplayName("When link is unaccepted")
        @Test
        void linkUnaccepted() {
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "tos_unaccepted.json").toString()
            );

            assertFalse(service.termsAccepted(termsOfServiceLink));
        }

        @DisplayName("When link is accepted")
        @Test
        void linkAccepted() {
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "tos_accepted.json").toString()
            );

            assertTrue(service.termsAccepted(termsOfServiceLink));
        }
    }

    @DisplayName("Test writeTermsLink")
    @Nested
    class WriteTermsLink {

        private final URI termsOfServiceLink = URI.create("http://localhost:8000");

        @DisplayName("When file format is invalid")
        @ParameterizedTest(name = "with accept = {0}")
        @ValueSource(booleans = {true, false})
        void invalidFormat(boolean accept) {
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "tos_invalid.json").toString()
            );

            assertThrows(SSLCertificateException.class, () -> service.writeTermsLink(termsOfServiceLink, accept));
        }

        @DisplayName("When terms of service file doesn't exist")
        @ParameterizedTest(name = "with accept = {0}")
        @CsvSource(value = {"true, YES", "false, NO"})
        void fileDoesNotExist(boolean accept, String expected) throws IOException {
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "not_exists").toString()
            );

            service.writeTermsLink(termsOfServiceLink, accept);

            assertLinesMatch(
                    Files.readAllLines(Paths.get("src", "test", "resources", "tos_expected.json")).stream()
                            .map(s -> s.replaceFirst("\\$expected", expected))
                            .collect(Collectors.toList()),
                    Files.readAllLines(Paths.get("src", "test", "resources", "not_exists"))
            );
        }

        @DisplayName("When terms of service file doesn't contain link")
        @ParameterizedTest(name = "with accept = {0}")
        @CsvSource(value = {"true, tos_accepted.json", "false, tos_unaccepted.json"})
        void fileDoesNotContainLink(boolean accept, String expectedFile) throws IOException {
            Files.copy(
                    Paths.get("src", "test", "resources", "tos_no_match.json"),
                    Paths.get("src", "test", "resources", "tos_no_match_copy")
            );
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "tos_no_match_copy").toString()
            );

            service.writeTermsLink(termsOfServiceLink, accept);

            assertLinesMatch(
                    Files.readAllLines(Paths.get("src", "test", "resources", expectedFile)),
                    Files.readAllLines(Paths.get("src", "test", "resources", "tos_no_match_copy"))
            );
        }

        @DisplayName("When terms of service file contains the link unaccepted")
        @ParameterizedTest(name = "with accept = {0}")
        @CsvSource(value = {"true, tos_accepted.json", "false, tos_unaccepted.json"})
        void fileContainsUnacceptedLink(boolean accept, String expectedFile) throws IOException {
            Files.copy(
                    Paths.get("src", "test", "resources", "tos_unaccepted.json"),
                    Paths.get("src", "test", "resources", "tos_unaccepted_copy")
            );
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "tos_unaccepted_copy").toString()
            );

            service.writeTermsLink(termsOfServiceLink, accept);

            assertLinesMatch(
                    Files.readAllLines(Paths.get("src", "test", "resources", expectedFile)),
                    Files.readAllLines(Paths.get("src", "test", "resources", "tos_unaccepted_copy"))
            );
        }

        @DisplayName("When terms of service file contains the link accepted")
        @ParameterizedTest(name = "with accept = {0}")
        @CsvSource(value = {"true, tos_accepted.json", "false, tos_unaccepted.json"})
        void fileContainsAcceptedLink(boolean accept, String expectedFile) throws IOException {
            Files.copy(
                    Paths.get("src", "test", "resources", "tos_accepted.json"),
                    Paths.get("src", "test", "resources", "tos_accepted_copy")
            );
            when(config.getTermsOfServiceFile()).thenReturn(
                    Paths.get("src", "test", "resources", "tos_accepted_copy").toString()
            );

            service.writeTermsLink(termsOfServiceLink, accept);

            assertLinesMatch(
                    Files.readAllLines(Paths.get("src", "test", "resources", expectedFile)),
                    Files.readAllLines(Paths.get("src", "test", "resources", "tos_accepted_copy"))
            );
        }

        @AfterEach
        void tearDown() throws IOException {
            Files.deleteIfExists(Paths.get("src", "test", "resources", "not_exists"));
            Files.deleteIfExists(Paths.get("src", "test", "resources", "tos_no_match_copy"));
            Files.deleteIfExists(Paths.get("src", "test", "resources", "tos_unaccepted_copy"));
            Files.deleteIfExists(Paths.get("src", "test", "resources", "tos_accepted_copy"));
        }
    }
}
