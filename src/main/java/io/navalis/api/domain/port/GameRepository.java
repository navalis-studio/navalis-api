package io.navalis.api.domain.port;

import io.navalis.api.domain.model.Game;

public interface GameRepository {
    void save(Game game);
}
