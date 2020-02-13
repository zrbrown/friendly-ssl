package net.eightlives.friendlyssl.exception;

public class UpdateFailedException extends RuntimeException {

    public UpdateFailedException() {
        super("ACME Resource update failed");
    }
}
