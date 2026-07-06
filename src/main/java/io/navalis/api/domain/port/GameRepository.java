package io.navalis.api.domain.port;

import io.navalis.api.domain.model.Game;

import java.util.Optional;
import java.util.UUID;

public interface GameRepository {
    Optional<Game> findById(UUID id);
    void save(Game game);
}
