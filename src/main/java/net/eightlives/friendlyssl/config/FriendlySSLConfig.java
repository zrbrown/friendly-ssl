package net.eightlives.friendlyssl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "friendly-ssl")
public class FriendlySSLConfig {

    private boolean autoRenewEnabled;
    private String acmeSessionUrl;
    private String domain;
    private String accountEmail;
    private String certificateFriendlyName;
    private String accountPrivateKeyFile;
    private String keystoreFile;
    private String termsOfServiceFile;
    private int orderTimeoutSeconds;
    private int tokenRequestedTimeoutSeconds;
    private int authChallengeTimeoutSeconds;
    private int autoRenewalHoursBefore;
    private int errorRetryWaitHours;

    public boolean isAutoRenewEnabled() {
        return autoRenewEnabled;
    }

    public void setAutoRenewEnabled(boolean autoRenewEnabled) {
        this.autoRenewEnabled = autoRenewEnabled;
    }

    public String getAcmeSessionUrl() {
        return acmeSessionUrl;
    }

    public void setAcmeSessionUrl(String acmeSessionUrl) {
        this.acmeSessionUrl = acmeSessionUrl;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getAccountEmail() {
        return accountEmail;
    }

    public void setAccountEmail(String accountEmail) {
        this.accountEmail = accountEmail;
    }

    public String getCertificateFriendlyName() {
        return certificateFriendlyName;
    }

    public void setCertificateFriendlyName(String certificateFriendlyName) {
        this.certificateFriendlyName = certificateFriendlyName;
    }

    public String getAccountPrivateKeyFile() {
        return accountPrivateKeyFile;
    }

    public void setAccountPrivateKeyFile(String accountPrivateKeyFile) {
        this.accountPrivateKeyFile = accountPrivateKeyFile;
    }

    public String getKeystoreFile() {
        return keystoreFile;
    }

    public void setKeystoreFile(String keystoreFile) {
        this.keystoreFile = keystoreFile;
    }

    public String getTermsOfServiceFile() {
        return termsOfServiceFile;
    }

    public void setTermsOfServiceFile(String termsOfServiceFile) {
        this.termsOfServiceFile = termsOfServiceFile;
    }

    public int getOrderTimeoutSeconds() {
        return orderTimeoutSeconds;
    }

    public void setOrderTimeoutSeconds(int orderTimeoutSeconds) {
        this.orderTimeoutSeconds = orderTimeoutSeconds;
    }

    public int getTokenRequestedTimeoutSeconds() {
        return tokenRequestedTimeoutSeconds;
    }

    public void setTokenRequestedTimeoutSeconds(int tokenRequestedTimeoutSeconds) {
        this.tokenRequestedTimeoutSeconds = tokenRequestedTimeoutSeconds;
    }

    public int getAuthChallengeTimeoutSeconds() {
        return authChallengeTimeoutSeconds;
    }

    public void setAuthChallengeTimeoutSeconds(int authChallengeTimeoutSeconds) {
        this.authChallengeTimeoutSeconds = authChallengeTimeoutSeconds;
    }

    public int getAutoRenewalHoursBefore() {
        return autoRenewalHoursBefore;
    }

    public void setAutoRenewalHoursBefore(int autoRenewalHoursBefore) {
        this.autoRenewalHoursBefore = autoRenewalHoursBefore;
    }

    public int getErrorRetryWaitHours() {
        return errorRetryWaitHours;
    }

    public void setErrorRetryWaitHours(int errorRetryWaitHours) {
        this.errorRetryWaitHours = errorRetryWaitHours;
    }
}
