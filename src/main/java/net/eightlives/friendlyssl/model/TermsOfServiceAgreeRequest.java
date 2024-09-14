package net.eightlives.friendlyssl.model;

import jakarta.validation.constraints.NotNull;

public class TermsOfServiceAgreeRequest {

    @NotNull
    private String termsOfServiceLink;

    public TermsOfServiceAgreeRequest() {
    }

    public TermsOfServiceAgreeRequest(String termsOfServiceLink) {
        this.termsOfServiceLink = termsOfServiceLink;
    }

    public String getTermsOfServiceLink() {
        return termsOfServiceLink;
    }

    public void setTermsOfServiceLink(String termsOfServiceLink) {
        this.termsOfServiceLink = termsOfServiceLink;
    }
}
