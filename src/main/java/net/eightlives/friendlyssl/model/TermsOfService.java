package net.eightlives.friendlyssl.model;

public class TermsOfService {

    private String termsOfService;
    private String agreeToTerms;

    public TermsOfService() {
    }

    public TermsOfService(String termsOfService, String agreeToTerms) {
        this.termsOfService = termsOfService;
        this.agreeToTerms = agreeToTerms;
    }

    public String getTermsOfService() {
        return termsOfService;
    }

    public String getAgreeToTerms() {
        return agreeToTerms;
    }
}
