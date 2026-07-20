package io.navalis.api.domain.model;

import io.navalis.api.domain.exception.GameAlreadyFullException;
import io.navalis.api.domain.exception.GameNotReadyException;
import io.navalis.api.domain.exception.NotYourTurnException;

import java.util.UUID;

public class Game {

    private final UUID id;
    private final String roomCode;
    private final Player player1;
    private Player player2;
    private GameStatus status;
    private UUID currentTurnPlayerId;
    private UUID winnerId;

    private Game(UUID id, String roomCode, Player player1) {
        this.id = id;
        this.roomCode = roomCode;
        this.player1 = player1;
        this.status = GameStatus.WAITING_FOR_OPPONENT;
    }

    public static Game create(UUID id, String roomCode, UUID playerId) {
        Player player = new Player(playerId);
        return new Game(id, roomCode, player);
    }

    public void join(UUID playerId) {
        if (player2 != null) {
            throw new GameAlreadyFullException();
        }
        this.player2 = new Player(playerId);
        this.status = GameStatus.PLACING_SHIPS;
    }

    public void placeShip(UUID playerId, ShipType shipType, Coordinate start, Orientation orientation) {
        if (status != GameStatus.PLACING_SHIPS) {
            throw new GameNotReadyException("Não é possível posicionar navios neste momento.");
        }

        Player player = getPlayerById(playerId);
        Ship ship = new Ship(shipType, start, orientation);
        player.getBoard().placeShip(ship);
    }

    public void markReady(UUID playerId) {
        if (status != GameStatus.PLACING_SHIPS) {
            throw new GameNotReadyException("Não é possível marcar pronto neste momento.");
        }

        Player player = getPlayerById(playerId);
        player.markReady();

        if (player1.isReady() && player2.isReady()) {
            this.status = GameStatus.IN_PROGRESS;
            this.currentTurnPlayerId = player1.getId();
        }
    }

    public void unmarkReady(UUID playerId) {
        if (status != GameStatus.PLACING_SHIPS) {
            throw new GameNotReadyException("Não é possível cancelar prontidão neste momento.");
        }

        Player player = getPlayerById(playerId);
        player.unmarkReady();
        player.getBoard().clear();
    }

    public ShotResult fire(UUID playerId, Coordinate target) {
        if (status != GameStatus.IN_PROGRESS) {
            throw new GameNotReadyException("Partida não está em andamento.");
        }
        if (!playerId.equals(currentTurnPlayerId)) {
            throw new NotYourTurnException();
        }

        Player opponent = getOpponent(playerId);
        ShotResult result = opponent.getBoard().receiveShot(target);

        if (opponent.getBoard().allShipsSunk()) {
            this.status = GameStatus.FINISHED;
            this.winnerId = playerId;
        } else if (result == ShotResult.MISS) {
            this.currentTurnPlayerId = opponent.getId();
        }

        return result;
    }

    /**
     * Forfeit (WO): the player who disconnects/abandons loses,
     * the remaining player wins automatically.
     */
    public void forfeit(UUID quitterId) {
        if (status == GameStatus.FINISHED) return;
        this.status = GameStatus.FINISHED;
        this.winnerId = getOpponent(quitterId).getId();
    }

    private Player getPlayerById(UUID playerId) {
        if (player1.getId().equals(playerId)) {
            return player1;
        }
        if (player2 != null && player2.getId().equals(playerId)) {
            return player2;
        }
        throw new IllegalArgumentException("Jogador não encontrado nesta partida.");
    }

    private Player getOpponent(UUID playerId) {
        if (player1.getId().equals(playerId)) {
            return player2;
        }
        return player1;
    }

    public UUID getId() {
        return id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public GameStatus getStatus() {
        return status;
    }

    public UUID getCurrentTurnPlayerId() {
        return currentTurnPlayerId;
    }

    public UUID getWinnerId() {
        return winnerId;
    }
}
