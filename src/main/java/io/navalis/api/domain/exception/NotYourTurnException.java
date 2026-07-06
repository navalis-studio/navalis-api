package io.navalis.api.domain.exception;

public class NotYourTurnException extends DomainException {

    public NotYourTurnException() {
        super("Não é o seu turno.");
    }
}
