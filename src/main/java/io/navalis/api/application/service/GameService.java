package io.navalis.api.application.service;

import io.navalis.api.application.dto.request.FireRequest;
import io.navalis.api.application.dto.request.PlaceShipRequest;
import io.navalis.api.application.dto.response.GameResponse;
import io.navalis.api.application.dto.response.ShotResponse;
import io.navalis.api.domain.exception.DomainException;
import io.navalis.api.domain.model.Coordinate;
import io.navalis.api.domain.model.Game;
import io.navalis.api.domain.model.GameStatus;
import io.navalis.api.domain.model.Ship;
import io.navalis.api.domain.model.ShipType;
import io.navalis.api.domain.model.ShotResult;
import io.navalis.api.domain.port.GameRepository;
import io.navalis.api.infrastructure.persistence.entity.GameEntity;
import io.navalis.api.infrastructure.persistence.repository.JpaGameRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private final JpaGameRepository jpaGameRepository;
    private final Map<UUID, Game> activeGames = new ConcurrentHashMap<>();

    public GameService(GameRepository gameRepository, JpaGameRepository jpaGameRepository) {
        this.gameRepository = gameRepository;
        this.jpaGameRepository = jpaGameRepository;
    }

    /**
     * On startup, remove games that were left in non-final states (WAITING/PLACING/IN_PROGRESS)
     * since they only live in memory and are lost on restart.
     */
    @PostConstruct
    public void cleanOrphanedGames() {
        List<String> nonFinalStatuses = List.of(
                GameStatus.WAITING_FOR_OPPONENT.name(),
                GameStatus.PLACING_SHIPS.name(),
                GameStatus.IN_PROGRESS.name()
        );
        jpaGameRepository.deleteByStatusIn(nonFinalStatuses);
    }

    public GameResponse createGame(UUID playerId) {
        UUID gameId = UUID.randomUUID();
        Game game = Game.create(gameId, playerId);
        activeGames.put(gameId, game);
        gameRepository.save(game); // Persist for listing available games

        return new GameResponse(gameId, game.getStatus(), "Partida criada. Aguardando oponente.");
    }

    public GameResponse joinGame(UUID gameId, UUID playerId) {
        Game game = getActiveGame(gameId);
        game.join(playerId);
        gameRepository.save(game); // Persist player2 joining

        return new GameResponse(gameId, game.getStatus(), "Oponente entrou. Posicione seus navios!");
    }

    public void placeShip(UUID gameId, UUID playerId, PlaceShipRequest request) {
        Game game = getActiveGame(gameId);
        Coordinate start = new Coordinate(request.row(), request.col());
        game.placeShip(playerId, request.shipType(), start, request.orientation());
        // No DB save - gameplay state lives in memory only
    }

    public void markReady(UUID gameId, UUID playerId) {
        Game game = getActiveGame(gameId);
        game.markReady(playerId);
        // No DB save - status transition handled in memory
    }

    public ShotResponse fire(UUID gameId, UUID playerId, FireRequest request) {
        Game game = getActiveGame(gameId);
        Coordinate target = new Coordinate(request.row(), request.col());
        ShotResult result = game.fire(playerId, target);

        ShipType sunkShipType = null;
        if (result == ShotResult.SUNK) {
            sunkShipType = findSunkShipAt(game, playerId, target);
        }

        boolean gameOver = game.getStatus() == GameStatus.FINISHED;

        // Only persist when game ends (save result)
        if (gameOver) {
            gameRepository.save(game);
            activeGames.remove(gameId);
        }

        return new ShotResponse(result, sunkShipType, gameOver, game.getWinnerId());
    }

    public List<GameResponse> findAvailableGames() {
        List<GameEntity> waitingGames = jpaGameRepository.findByStatus(GameStatus.WAITING_FOR_OPPONENT.name());
        return waitingGames.stream()
                .map(entity -> new GameResponse(
                        entity.getId(),
                        GameStatus.valueOf(entity.getStatus()),
                        "Aguardando oponente."))
                .toList();
    }

    public void cancelGame(UUID gameId, UUID playerId) {
        Game game = activeGames.get(gameId);
        if (game == null) {
            throw new DomainException("Partida não encontrada: " + gameId);
        }
        if (!game.getPlayer1().getId().equals(playerId)) {
            throw new DomainException("Apenas o criador pode cancelar a partida.");
        }
        if (game.getStatus() != GameStatus.WAITING_FOR_OPPONENT) {
            throw new DomainException("Só é possível cancelar partidas aguardando oponente.");
        }
        activeGames.remove(gameId);
        jpaGameRepository.deleteById(gameId);
    }

    public GameResponse getGameInfo(UUID gameId) {
        Game game = getActiveGame(gameId);
        return new GameResponse(gameId, game.getStatus(), null);
    }

    public UUID getCurrentTurnPlayerId(UUID gameId) {
        Game game = getActiveGame(gameId);
        return game.getCurrentTurnPlayerId();
    }

    private Game getActiveGame(UUID gameId) {
        Game game = activeGames.get(gameId);
        if (game == null) {
            throw new DomainException("Partida não encontrada: " + gameId);
        }
        return game;
    }

    private ShipType findSunkShipAt(Game game, UUID shooterId, Coordinate target) {
        var opponent = game.getPlayer1().getId().equals(shooterId)
                ? game.getPlayer2()
                : game.getPlayer1();

        if (opponent != null) {
            for (Ship ship : opponent.getBoard().getShips()) {
                if (ship.isSunk() && ship.getOccupiedCoordinates().contains(target)) {
                    return ship.getType();
                }
            }
        }
        return null;
    }
}
