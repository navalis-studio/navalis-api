package io.navalis.api.domain.exception;

public class GameNotReadyException extends DomainException {

    public GameNotReadyException(String message) {
        super(message);
    }
}
