package io.navalis.api.infrastructure.persistence.mapper;

import io.navalis.api.domain.model.Game;
import io.navalis.api.domain.model.GameStatus;
import io.navalis.api.infrastructure.persistence.entity.GameEntity;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Component
public class GameMapper {

    private final ObjectMapper objectMapper;

    public GameMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GameEntity toEntity(Game game) {
        GameEntity entity = new GameEntity();
        entity.setId(game.getId());
        entity.setPlayer1Id(game.getPlayer1().getId());
        entity.setPlayer2Id(game.getPlayer2() != null ? game.getPlayer2().getId() : null);
        entity.setStatus(game.getStatus().name());
        entity.setCurrentTurnPlayerId(game.getCurrentTurnPlayerId());
        entity.setWinnerId(game.getWinnerId());
        entity.setGameState(serializeGameState(game));
        entity.setFinishedAt(game.getStatus() == GameStatus.FINISHED ? LocalDateTime.now() : null);
        return entity;
    }

    public GameEntity updateEntity(GameEntity entity, Game game) {
        entity.setPlayer2Id(game.getPlayer2() != null ? game.getPlayer2().getId() : null);
        entity.setStatus(game.getStatus().name());
        entity.setCurrentTurnPlayerId(game.getCurrentTurnPlayerId());
        entity.setWinnerId(game.getWinnerId());
        entity.setGameState(serializeGameState(game));
        entity.setFinishedAt(game.getStatus() == GameStatus.FINISHED ? LocalDateTime.now() : null);
        return entity;
    }

    private String serializeGameState(Game game) {
        try {
            return objectMapper.writeValueAsString(game);
        } catch (JacksonException e) {
            throw new RuntimeException("Erro ao serializar estado do jogo.", e);
        }
    }
}
