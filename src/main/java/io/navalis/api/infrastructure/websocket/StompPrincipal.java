package io.navalis.api.infrastructure.websocket;

import java.security.Principal;
import java.util.UUID;

public class StompPrincipal implements Principal {

    private final UUID userId;
    private final String username;

    public StompPrincipal(UUID userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    public UUID getUserId() {
        return userId;
    }
}
