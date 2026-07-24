package io.navalis.api.domain.exception;

public class GameAlreadyFullException extends DomainException {

    public GameAlreadyFullException() {
        super("error.game_full");
    }
}
