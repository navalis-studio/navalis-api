package io.navalis.api.domain.exception;

public class NotYourTurnException extends DomainException {

    public NotYourTurnException() {
        super("error.not_your_turn");
    }
}
