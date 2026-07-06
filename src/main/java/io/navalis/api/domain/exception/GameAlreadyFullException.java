package io.navalis.api.domain.exception;

public class GameAlreadyFullException extends DomainException {

    public GameAlreadyFullException() {
        super("Partida já está cheia.");
    }
}
