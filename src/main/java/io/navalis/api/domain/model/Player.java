package io.navalis.api.domain.model;

import java.util.UUID;

public class Player {
    private final UUID id;
    private final Board board;
    private boolean ready;

    public Player(UUID id) {
        this.id = id;
        this.board = new Board();
        this.ready = false;
    }

    public void markReady() {
        this.ready = true;
    }

    public UUID getId() {
        return id;
    }

    public Board getBoard() {
        return board;
    }

    public boolean isReady() {
        return ready;
    }
}
