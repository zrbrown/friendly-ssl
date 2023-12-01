package net.eightlives.friendlyssl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.FriendlySSLException;
import net.eightlives.friendlyssl.model.TermsOfService;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.exception.AcmeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class TermsOfServiceService {

    private static final Logger LOG = LoggerFactory.getLogger(TermsOfServiceService.class);

    private static final String AGREE_TO_TERMS_YES = "YES";
    private static final String AGREE_TO_TERMS_NO = "NO";

    private final FriendlySSLConfig config;
    private final ObjectMapper objectMapper;

    public TermsOfServiceService(FriendlySSLConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the terms of service link from the given session.
     *
     * @param session the session from which to retrieve the terms of service link
     * @return the terms of service {@link URI link}
     * @throws FriendlySSLException if the terms of service link could not be retrieved from the given session
     */
    public URI getTermsOfServiceLink(Session session) {
        URI termsOfServiceLink;
        try {
            termsOfServiceLink = session.getMetadata().getTermsOfService();
        } catch (AcmeException e) {
            LOG.error("Could not retrieve terms of service link", e);
            throw new FriendlySSLException(e);
        }

        if (termsOfServiceLink == null) {
            LOG.error("Could not retrieve terms of service link");
            throw new FriendlySSLException("Terms of service should not be null. There may be a problem with the provider.");
        }

        return termsOfServiceLink;
    }

    /**
     * Returns whether or not the given terms of service link has been accepted.
     *
     * @param termsOfServiceLink the terms of service link to check for acceptance
     * @return {@code true} if {@code termsOfServiceLink} has been accepted, {@code false} otherwise
     * @throws FriendlySSLException if exception occurs while accessing terms of service file
     */
    public boolean termsAccepted(URI termsOfServiceLink) {
        try {
            TermsOfService[] termsOfService = objectMapper.readValue(
                    Files.newInputStream(Path.of(config.getTermsOfServiceFile())),
                    TermsOfService[].class);
            return Stream.of(termsOfService)
                    .filter(tos -> termsOfServiceLink.toString().equals(tos.getTermsOfService()))
                    .anyMatch(tos -> tos.getAgreeToTerms().equalsIgnoreCase(AGREE_TO_TERMS_YES));
        } catch (NoSuchFileException e) {
            return false;
        } catch (IOException e) {
            LOG.error("Exception while trying to read from terms of service file " + config.getTermsOfServiceFile(), e);
            throw new FriendlySSLException(e);
        }
    }

    /**
     * Writes an acceptance state for the given terms of service link.
     *
     * @param termsOfServiceLink the terms of service link for which to write the given acceptance state
     * @param accept             {@code true} to accept {@code termsOfServiceLink}, {@code false} to not accept
     * @throws FriendlySSLException if exception occurs while accessing or writing to terms of service file
     */
    public void writeTermsLink(URI termsOfServiceLink, boolean accept) {
        Path termsOfServiceFile = Path.of(config.getTermsOfServiceFile());

        try {
            Files.createFile(termsOfServiceFile);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(termsOfServiceFile), new TermsOfService[0]
            );
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) {
            LOG.error("Exception while creating terms of service file " + config.getTermsOfServiceFile(), e);
            throw new FriendlySSLException(e);
        }

        try {
            TermsOfService[] termsOfService = objectMapper.readValue(Files.newBufferedReader(termsOfServiceFile), TermsOfService[].class);
            List<TermsOfService> allTerms = Stream.of(termsOfService)
                    .filter(tos -> !termsOfServiceLink.toString().equals(tos.getTermsOfService()))
                    .collect(Collectors.toList());
            allTerms.add(new TermsOfService(termsOfServiceLink.toString(), accept ? AGREE_TO_TERMS_YES : AGREE_TO_TERMS_NO));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(Files.newBufferedWriter(termsOfServiceFile), allTerms);
        } catch (IOException e) {
            LOG.error("Exception while trying to read or write to terms of service file " + config.getTermsOfServiceFile(), e);
            throw new FriendlySSLException(e);
        }
    }
}
