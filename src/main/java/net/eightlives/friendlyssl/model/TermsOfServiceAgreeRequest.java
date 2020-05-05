package net.eightlives.friendlyssl.model;

import javax.validation.constraints.NotNull;

public class TermsOfServiceAgreeRequest {

    @NotNull
    private final String termsOfServiceLink;

    public TermsOfServiceAgreeRequest(String termsOfServiceLink) {
        this.termsOfServiceLink = termsOfServiceLink;
    }

    public String getTermsOfServiceLink() {
        return termsOfServiceLink;
    }
}
