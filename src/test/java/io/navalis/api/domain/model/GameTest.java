package io.navalis.api.domain.model;

import io.navalis.api.domain.exception.GameAlreadyFullException;
import io.navalis.api.domain.exception.GameNotReadyException;
import io.navalis.api.domain.exception.NotYourTurnException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameTest {

    private static final UUID PLAYER1_ID = UUID.randomUUID();
    private static final UUID PLAYER2_ID = UUID.randomUUID();
    private static final UUID GAME_ID = UUID.randomUUID();
    private static final String ROOM_CODE = "ABCDEF";

    private Game game;

    @BeforeEach
    void setUp() {
        game = Game.create(GAME_ID, ROOM_CODE, PLAYER1_ID);
    }

    @Nested
    class Creation {

        @Test
        void shouldCreateGameWithCorrectInitialState() {
            assertEquals(GAME_ID, game.getId());
            assertEquals(ROOM_CODE, game.getRoomCode());
            assertEquals(GameStatus.WAITING_FOR_OPPONENT, game.getStatus());
            assertNotNull(game.getPlayer1());
            assertNull(game.getPlayer2());
            assertNull(game.getCurrentTurnPlayerId());
            assertNull(game.getWinnerId());
        }
    }

    @Nested
    class Joining {

        @Test
        void shouldTransitionToPlacingShipsOnJoin() {
            game.join(PLAYER2_ID);

            assertEquals(GameStatus.PLACING_SHIPS, game.getStatus());
            assertNotNull(game.getPlayer2());
            assertEquals(PLAYER2_ID, game.getPlayer2().getId());
        }

        @Test
        void shouldRejectThirdPlayer() {
            game.join(PLAYER2_ID);
            UUID thirdPlayer = UUID.randomUUID();

            assertThrows(GameAlreadyFullException.class, () -> game.join(thirdPlayer));
        }
    }

    @Nested
    class ShipPlacement {

        @BeforeEach
        void joinGame() {
            game.join(PLAYER2_ID);
        }

        @Test
        void shouldAllowPlacingShipsDuringPlacingPhase() {
            assertDoesNotThrow(() ->
                    game.placeShip(PLAYER1_ID, ShipType.CARRIER, new Coordinate(0, 0), Orientation.HORIZONTAL));
        }

        @Test
        void shouldRejectPlacingShipsBeforeJoin() {
            Game freshGame = Game.create(UUID.randomUUID(), "XYZABC", PLAYER1_ID);

            assertThrows(GameNotReadyException.class, () ->
                    freshGame.placeShip(PLAYER1_ID, ShipType.CARRIER, new Coordinate(0, 0), Orientation.HORIZONTAL));
        }

        @Test
        void shouldRejectUnknownPlayer() {
            UUID stranger = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class, () ->
                    game.placeShip(stranger, ShipType.CARRIER, new Coordinate(0, 0), Orientation.HORIZONTAL));
        }
    }

    @Nested
    class ReadyAndStart {

        @BeforeEach
        void joinAndPlaceShips() {
            game.join(PLAYER2_ID);
            // Place ships for both players
            game.placeShip(PLAYER1_ID, ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
            game.placeShip(PLAYER2_ID, ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        }

        @Test
        void shouldNotStartGameWhenOnlyOnePlayerReady() {
            game.markReady(PLAYER1_ID);

            assertEquals(GameStatus.PLACING_SHIPS, game.getStatus());
            assertNull(game.getCurrentTurnPlayerId());
        }

        @Test
        void shouldStartGameWhenBothPlayersReady() {
            game.markReady(PLAYER1_ID);
            game.markReady(PLAYER2_ID);

            assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
            assertEquals(PLAYER1_ID, game.getCurrentTurnPlayerId());
        }

        @Test
        void shouldRejectReadyInWrongPhase() {
            game.markReady(PLAYER1_ID);
            game.markReady(PLAYER2_ID);
            // Game is now IN_PROGRESS, cannot mark ready again
            UUID anotherPlayer = UUID.randomUUID();
            Game anotherGame = Game.create(UUID.randomUUID(), "QQQQQQ", anotherPlayer);
            // Cannot mark ready in WAITING_FOR_OPPONENT
            assertThrows(GameNotReadyException.class, () -> anotherGame.markReady(anotherPlayer));
        }
    }

    @Nested
    class Firing {

        @BeforeEach
        void setupGameInProgress() {
            game.join(PLAYER2_ID);
            // Player1 places a ship
            game.placeShip(PLAYER1_ID, ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
            // Player2 places a ship
            game.placeShip(PLAYER2_ID, ShipType.DESTROYER, new Coordinate(5, 5), Orientation.HORIZONTAL);
            game.markReady(PLAYER1_ID);
            game.markReady(PLAYER2_ID);
            // Game is now IN_PROGRESS, Player1's turn
        }

        @Test
        void shouldAllowCurrentPlayerToFire() {
            ShotResult result = game.fire(PLAYER1_ID, new Coordinate(5, 5));
            assertEquals(ShotResult.HIT, result);
        }

        @Test
        void shouldRejectFireWhenNotYourTurn() {
            assertThrows(NotYourTurnException.class, () ->
                    game.fire(PLAYER2_ID, new Coordinate(0, 0)));
        }

        @Test
        void shouldSwitchTurnOnMiss() {
            game.fire(PLAYER1_ID, new Coordinate(9, 9)); // miss

            assertEquals(PLAYER2_ID, game.getCurrentTurnPlayerId());
        }

        @Test
        void shouldKeepTurnOnHit() {
            game.fire(PLAYER1_ID, new Coordinate(5, 5)); // hit

            assertEquals(PLAYER1_ID, game.getCurrentTurnPlayerId());
        }

        @Test
        void shouldFinishGameWhenAllShipsSunk() {
            // Player2 has Destroyer at (5,5)-(5,6)
            game.fire(PLAYER1_ID, new Coordinate(5, 5));
            game.fire(PLAYER1_ID, new Coordinate(5, 6));

            assertEquals(GameStatus.FINISHED, game.getStatus());
            assertEquals(PLAYER1_ID, game.getWinnerId());
        }

        @Test
        void shouldRejectFireAfterGameFinished() {
            // Finish the game
            game.fire(PLAYER1_ID, new Coordinate(5, 5));
            game.fire(PLAYER1_ID, new Coordinate(5, 6));

            assertThrows(GameNotReadyException.class, () ->
                    game.fire(PLAYER2_ID, new Coordinate(0, 0)));
        }
    }

    @Nested
    class Forfeit {

        @BeforeEach
        void setupGameInProgress() {
            game.join(PLAYER2_ID);
            game.placeShip(PLAYER1_ID, ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
            game.placeShip(PLAYER2_ID, ShipType.DESTROYER, new Coordinate(5, 5), Orientation.HORIZONTAL);
            game.markReady(PLAYER1_ID);
            game.markReady(PLAYER2_ID);
        }

        @Test
        void shouldDeclareOpponentWinnerOnForfeit() {
            game.forfeit(PLAYER1_ID);

            assertEquals(GameStatus.FINISHED, game.getStatus());
            assertEquals(PLAYER2_ID, game.getWinnerId());
        }

        @Test
        void shouldDeclarePlayer1WinnerWhenPlayer2Forfeits() {
            game.forfeit(PLAYER2_ID);

            assertEquals(GameStatus.FINISHED, game.getStatus());
            assertEquals(PLAYER1_ID, game.getWinnerId());
        }

        @Test
        void shouldDoNothingIfAlreadyFinished() {
            game.forfeit(PLAYER1_ID);
            UUID winnerBefore = game.getWinnerId();

            game.forfeit(PLAYER2_ID); // should be ignored

            assertEquals(winnerBefore, game.getWinnerId());
        }
    }
}
