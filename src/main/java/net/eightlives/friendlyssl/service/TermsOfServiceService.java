package net.eightlives.friendlyssl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.model.TermsOfService;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.exception.AcmeException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class TermsOfServiceService {

    private static final String AGREE_TO_TERMS_YES = "YES";
    private static final String AGREE_TO_TERMS_NO = "NO";

    private final FriendlySSLConfig config;
    private final ObjectMapper objectMapper;

    public TermsOfServiceService(FriendlySSLConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public URI getTermsOfServiceLink(Session session) {
        URI termsOfServiceLink;
        try {
            termsOfServiceLink = session.getMetadata().getTermsOfService();
        } catch (AcmeException e) {
            log.error("Could not retrieve terms of service link", e);
            throw new SSLCertificateException(e);
        }

        if (termsOfServiceLink == null) {
            log.error("Could not retrieve terms of service link");
            throw new SSLCertificateException(new IllegalStateException("Terms of service should not be null. There may be a problem with the provider."));
        }

        return termsOfServiceLink;
    }

    public boolean termsAccepted(URI termsOfServiceLink) {
        File termsOfServiceFile = new File(config.getTermsOfServiceFile());

        if (!termsOfServiceFile.exists()) {
            return false;
        }

        try {
            TermsOfService[] termsOfService = objectMapper.readValue(termsOfServiceFile, TermsOfService[].class);
            return Stream.of(termsOfService)
                    .filter(tos -> termsOfServiceLink.toString().equals(tos.getTermsOfService()))
                    .anyMatch(tos -> tos.getAgreeToTerms().equalsIgnoreCase(AGREE_TO_TERMS_YES));
        } catch (IOException e) {
            log.error("Exception while trying to read from terms of service file " + config.getTermsOfServiceFile(), e);
            throw new SSLCertificateException(e);
        }
    }

    public void writeTermsLink(URI termsOfServiceLink, boolean accept) {
        Path termsOfServiceFile = Path.of(config.getTermsOfServiceFile());

        try {
            Files.createFile(termsOfServiceFile);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(termsOfServiceFile), new TermsOfService[0]
            );
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) {
            log.error("Exception while creating terms of service file " + config.getTermsOfServiceFile(), e);
            throw new SSLCertificateException(e);
        }

        try {
            TermsOfService[] termsOfService = objectMapper.readValue(Files.newBufferedReader(termsOfServiceFile), TermsOfService[].class);
            List<TermsOfService> allTerms = Stream.of(termsOfService)
                    .filter(tos -> !termsOfServiceLink.toString().equals(tos.getTermsOfService()))
                    .collect(Collectors.toList());
            allTerms.add(new TermsOfService(termsOfServiceLink.toString(), accept ? AGREE_TO_TERMS_YES : AGREE_TO_TERMS_NO));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(Files.newBufferedWriter(termsOfServiceFile), allTerms);
        } catch (IOException e) {
            log.error("Exception while trying to read or write to terms of service file " + config.getTermsOfServiceFile(), e);
            throw new SSLCertificateException(e);
        }
    }
}
