package io.navalis.api.application.service;

import io.navalis.api.application.dto.request.FireRequest;
import io.navalis.api.application.dto.request.PlaceShipRequest;
import io.navalis.api.application.dto.response.GameResponse;
import io.navalis.api.application.dto.response.ReconnectResponse;
import io.navalis.api.application.dto.response.ShotResponse;
import io.navalis.api.domain.exception.DomainException;
import io.navalis.api.domain.model.Coordinate;
import io.navalis.api.domain.model.Game;
import io.navalis.api.domain.model.GameStatus;
import io.navalis.api.domain.model.CellState;
import io.navalis.api.domain.model.Orientation;
import io.navalis.api.domain.model.Ship;
import io.navalis.api.domain.model.ShipType;
import io.navalis.api.domain.model.ShotResult;
import io.navalis.api.domain.port.GameRepository;
import io.navalis.api.infrastructure.persistence.repository.JpaGameRepository;
import io.navalis.api.infrastructure.persistence.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private static final String ROOM_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int ROOM_CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GameRepository gameRepository;
    private final JpaGameRepository jpaGameRepository;
    private final UserRepository userRepository;
    private final Map<UUID, Game> activeGames = new ConcurrentHashMap<>();

    public GameService(GameRepository gameRepository, JpaGameRepository jpaGameRepository, UserRepository userRepository) {
        this.gameRepository = gameRepository;
        this.jpaGameRepository = jpaGameRepository;
        this.userRepository = userRepository;
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
        String roomCode = generateRoomCode();
        Game game = Game.create(gameId, roomCode, playerId);
        activeGames.put(gameId, game);
        gameRepository.save(game); // Persist for listing available games

        return new GameResponse(gameId, roomCode, game.getStatus(), "Partida criada. Aguardando oponente.", null);
    }

    public GameResponse joinGame(UUID gameId, UUID playerId) {
        Game game = getActiveGame(gameId);
        game.join(playerId);
        gameRepository.save(game); // Persist player2 joining

        return new GameResponse(gameId, game.getRoomCode(), game.getStatus(), "Oponente entrou. Posicione seus navios!", null);
    }

    public GameResponse joinByRoomCode(String roomCode, UUID playerId) {
        Game game = findActiveGameByRoomCode(roomCode);
        UUID gameId = game.getId();
        game.join(playerId);
        gameRepository.save(game);

        return new GameResponse(gameId, game.getRoomCode(), game.getStatus(), "Oponente entrou. Posicione seus navios!", null);
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
        List<int[]> sunkShipCells = null;
        if (result == ShotResult.SUNK) {
            sunkShipType = findSunkShipAt(game, playerId, target);
            // Get all coordinates of the sunk ship
            var opponent = game.getPlayer1().getId().equals(playerId)
                    ? game.getPlayer2()
                    : game.getPlayer1();
            if (opponent != null) {
                for (Ship ship : opponent.getBoard().getShips()) {
                    if (ship.isSunk() && ship.getOccupiedCoordinates().contains(target)) {
                        sunkShipCells = ship.getOccupiedCoordinates().stream()
                                .map(c -> new int[]{c.row(), c.col()})
                                .toList();
                        break;
                    }
                }
            }
        }

        boolean gameOver = game.getStatus() == GameStatus.FINISHED;

        // Only persist when game ends (save result)
        if (gameOver) {
            gameRepository.save(game);
            updatePlayerStats(game.getWinnerId(), getLoser(game));
            activeGames.remove(gameId);
        }

        return new ShotResponse(result, sunkShipType, sunkShipCells, gameOver, game.getWinnerId());
    }

    public List<GameResponse> findAvailableGames() {
        List<Object[]> results = jpaGameRepository.findByStatusWithHostUsername(GameStatus.WAITING_FOR_OPPONENT.name());
        return results.stream()
                .map(row -> new GameResponse(
                        (UUID) row[0],
                        (String) row[1],
                        GameStatus.valueOf((String) row[2]),
                        null,
                        (String) row[3]))
                .toList();
    }

    @Transactional
    public void cancelGame(UUID gameId, UUID playerId) {
        logger.info("cancelGame chamado: gameId={}, playerId={}", gameId, playerId);
        Game game = activeGames.get(gameId);
        if (game == null) {
            logger.info("Game não encontrado em activeGames, deletando do banco");
            jpaGameRepository.deleteById(gameId);
            return;
        }

        // Only allow cancel before game is in progress
        if (game.getStatus() == GameStatus.IN_PROGRESS || game.getStatus() == GameStatus.FINISHED) {
            throw new DomainException("Não é possível cancelar uma partida em andamento.");
        }

        logger.info("Removendo game {} do activeGames e do banco", gameId);
        activeGames.remove(gameId);
        jpaGameRepository.deleteById(gameId);
    }

    public GameResponse getGameInfo(UUID gameId) {
        Game game = getActiveGame(gameId);
        return new GameResponse(gameId, game.getRoomCode(), game.getStatus(), null, null);
    }

    /**
     * Handle player forfeit/disconnect.
     * - WAITING_FOR_OPPONENT: just cancel (delete from DB)
     * - PLACING_SHIPS: cancel the match, both players return to lobby
     * - IN_PROGRESS: WO - remaining player wins
     */
    public Game forfeit(UUID gameId, UUID quitterId) {
        Game game = activeGames.get(gameId);
        if (game == null) return null;
        if (game.getStatus() == GameStatus.FINISHED) return null;

        // Only count as WO victory if game was in progress
        if (game.getStatus() == GameStatus.IN_PROGRESS) {
            game.forfeit(quitterId);
            gameRepository.save(game);
            updatePlayerStats(game.getWinnerId(), quitterId);
            activeGames.remove(gameId);
            return game;
        }

        // WAITING_FOR_OPPONENT or PLACING_SHIPS: just cancel, no winner
        activeGames.remove(gameId);
        jpaGameRepository.deleteById(gameId);
        return null;
    }

    /**
     * Find the gameId a player is currently in.
     */
    public UUID findGameByPlayer(UUID playerId) {
        for (var entry : activeGames.entrySet()) {
            Game game = entry.getValue();
            if (game.getPlayer1().getId().equals(playerId)) return entry.getKey();
            if (game.getPlayer2() != null && game.getPlayer2().getId().equals(playerId)) return entry.getKey();
        }
        return null;
    }

    public UUID getCurrentTurnPlayerId(UUID gameId) {
        Game game = getActiveGame(gameId);
        return game.getCurrentTurnPlayerId();
    }

    /**
     * Build reconnect data for a player returning to an active game.
     */
    public ReconnectResponse getReconnectData(UUID playerId) {
        UUID gameId = findGameByPlayer(playerId);
        if (gameId == null) return null;

        Game game = activeGames.get(gameId);
        if (game == null) return null;
        if (game.getStatus() == GameStatus.FINISHED) return null;

        var player = game.getPlayer1().getId().equals(playerId) ? game.getPlayer1() : game.getPlayer2();
        var opponent = game.getPlayer1().getId().equals(playerId) ? game.getPlayer2() : game.getPlayer1();

        // My ships
        List<ReconnectResponse.ShipData> myShips = new ArrayList<>();
        for (Ship ship : player.getBoard().getShips()) {
            myShips.add(new ReconnectResponse.ShipData(
                    ship.getType().name(),
                    ship.getStart().row(),
                    ship.getStart().col(),
                    ship.getOrientation().name()
            ));
        }

        // Shots I fired at enemy (opponent's board shots)
        List<ReconnectResponse.ShotData> myShots = new ArrayList<>();
        if (opponent != null) {
            for (var entry : opponent.getBoard().getShots().entrySet()) {
                myShots.add(new ReconnectResponse.ShotData(
                        entry.getKey().row(),
                        entry.getKey().col(),
                        entry.getValue().name()
                ));
            }
        }

        // Shots enemy fired at me (my board shots)
        List<ReconnectResponse.ShotData> enemyShots = new ArrayList<>();
        for (var entry : player.getBoard().getShots().entrySet()) {
            enemyShots.add(new ReconnectResponse.ShotData(
                    entry.getKey().row(),
                    entry.getKey().col(),
                    entry.getValue().name()
            ));
        }

        // Sunk enemy ships
        List<String> sunkEnemyShips = new ArrayList<>();
        if (opponent != null) {
            for (Ship ship : opponent.getBoard().getShips()) {
                if (ship.isSunk()) {
                    sunkEnemyShips.add(ship.getType().name());
                }
            }
        }

        // My sunk ships
        List<String> sunkMyShips = new ArrayList<>();
        for (Ship ship : player.getBoard().getShips()) {
            if (ship.isSunk()) {
                sunkMyShips.add(ship.getType().name());
            }
        }

        boolean myTurn = playerId.equals(game.getCurrentTurnPlayerId());
        boolean opponentReady = opponent != null && opponent.isReady();
        boolean myReady = player.isReady();

        return new ReconnectResponse(
                gameId,
                game.getRoomCode(),
                game.getStatus(),
                myTurn,
                myShips,
                myShots,
                enemyShots,
                sunkEnemyShips,
                sunkMyShips,
                opponentReady,
                myReady
        );
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

    private String generateRoomCode() {
        StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            code.append(ROOM_CODE_CHARS.charAt(RANDOM.nextInt(ROOM_CODE_CHARS.length())));
        }
        return code.toString();
    }

    private Game findActiveGameByRoomCode(String roomCode) {
        for (Game game : activeGames.values()) {
            if (game.getRoomCode().equalsIgnoreCase(roomCode)) {
                return game;
            }
        }
        throw new DomainException("Sala não encontrada: " + roomCode);
    }

    private void updatePlayerStats(UUID winnerId, UUID loserId) {
        userRepository.findById(winnerId).ifPresent(winner -> {
            winner.setWins(winner.getWins() + 1);
            userRepository.save(winner);
        });
        userRepository.findById(loserId).ifPresent(loser -> {
            loser.setLosses(loser.getLosses() + 1);
            userRepository.save(loser);
        });
    }

    private UUID getLoser(Game game) {
        UUID winnerId = game.getWinnerId();
        if (game.getPlayer1().getId().equals(winnerId)) {
            return game.getPlayer2().getId();
        }
        return game.getPlayer1().getId();
    }
}
