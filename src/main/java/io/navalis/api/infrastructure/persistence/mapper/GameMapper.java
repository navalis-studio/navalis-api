package io.navalis.api.infrastructure.persistence.mapper;

import io.navalis.api.domain.model.Game;
import io.navalis.api.domain.model.GameStatus;
import io.navalis.api.infrastructure.persistence.entity.GameEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class GameMapper {

    public GameEntity toEntity(Game game) {
        GameEntity entity = new GameEntity();
        entity.setId(game.getId());
        entity.setPlayer1Id(game.getPlayer1().getId());
        entity.setPlayer2Id(game.getPlayer2() != null ? game.getPlayer2().getId() : null);
        entity.setStatus(game.getStatus().name());
        entity.setWinnerId(game.getWinnerId());
        entity.setFinishedAt(game.getStatus() == GameStatus.FINISHED ? LocalDateTime.now() : null);
        return entity;
    }

    public GameEntity updateEntity(GameEntity entity, Game game) {
        entity.setPlayer2Id(game.getPlayer2() != null ? game.getPlayer2().getId() : null);
        entity.setStatus(game.getStatus().name());
        entity.setWinnerId(game.getWinnerId());
        entity.setFinishedAt(game.getStatus() == GameStatus.FINISHED ? LocalDateTime.now() : null);
        return entity;
    }
}
