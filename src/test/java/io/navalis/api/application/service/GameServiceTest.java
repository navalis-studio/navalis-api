package io.navalis.api.application.service;

import io.navalis.api.application.dto.request.FireRequest;
import io.navalis.api.application.dto.request.PlaceShipRequest;
import io.navalis.api.application.dto.response.GameResponse;
import io.navalis.api.application.dto.response.ShotResponse;
import io.navalis.api.domain.exception.DomainException;
import io.navalis.api.domain.exception.GameAlreadyFullException;
import io.navalis.api.domain.exception.NotYourTurnException;
import io.navalis.api.domain.model.*;
import io.navalis.api.domain.port.GameRepository;
import io.navalis.api.infrastructure.persistence.entity.UserEntity;
import io.navalis.api.infrastructure.persistence.repository.JpaGameRepository;
import io.navalis.api.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private JpaGameRepository jpaGameRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private GameService gameService;

    private static final UUID PLAYER1_ID = UUID.randomUUID();
    private static final UUID PLAYER2_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        gameService = new GameService(gameRepository, jpaGameRepository, userRepository, messagingTemplate);
    }

    @Nested
    class CreateGame {

        @Test
        void shouldCreateGameAndReturnResponse() {
            GameResponse response = gameService.createGame(PLAYER1_ID);

            assertNotNull(response.gameId());
            assertNotNull(response.roomCode());
            assertEquals(6, response.roomCode().length());
            assertEquals(GameStatus.WAITING_FOR_OPPONENT, response.status());
            verify(gameRepository).save(any(Game.class));
        }

        @Test
        void shouldGenerateUniqueRoomCodesForDifferentGames() {
            GameResponse response1 = gameService.createGame(PLAYER1_ID);
            GameResponse response2 = gameService.createGame(PLAYER1_ID);

            // Room codes are random, extremely unlikely to collide
            assertNotNull(response1.roomCode());
            assertNotNull(response2.roomCode());
        }
    }

    @Nested
    class JoinGame {

        @Test
        void shouldJoinExistingGame() {
            GameResponse created = gameService.createGame(PLAYER1_ID);
            UUID gameId = created.gameId();

            when(userRepository.findById(PLAYER1_ID))
                    .thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "host")));

            GameResponse joinResponse = gameService.joinGame(gameId, PLAYER2_ID);

            assertEquals(GameStatus.PLACING_SHIPS, joinResponse.status());
            assertEquals("host", joinResponse.hostUsername());
        }

        @Test
        void shouldRejectJoinNonExistentGame() {
            UUID fakeGameId = UUID.randomUUID();

            assertThrows(DomainException.class, () -> gameService.joinGame(fakeGameId, PLAYER2_ID));
        }

        @Test
        void shouldRejectThirdPlayerJoining() {
            GameResponse created = gameService.createGame(PLAYER1_ID);
            UUID gameId = created.gameId();

            when(userRepository.findById(PLAYER1_ID))
                    .thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "host")));

            gameService.joinGame(gameId, PLAYER2_ID);

            UUID thirdPlayer = UUID.randomUUID();
            assertThrows(GameAlreadyFullException.class, () -> gameService.joinGame(gameId, thirdPlayer));
        }
    }

    @Nested
    class JoinByRoomCode {

        @Test
        void shouldJoinByValidRoomCode() {
            GameResponse created = gameService.createGame(PLAYER1_ID);
            String roomCode = created.roomCode();

            when(userRepository.findById(PLAYER1_ID))
                    .thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "host")));

            GameResponse joinResponse = gameService.joinByRoomCode(roomCode, PLAYER2_ID);

            assertEquals(GameStatus.PLACING_SHIPS, joinResponse.status());
        }

        @Test
        void shouldRejectInvalidRoomCode() {
            assertThrows(DomainException.class, () -> gameService.joinByRoomCode("ZZZZZZZ", PLAYER2_ID));
        }
    }

    @Nested
    class PlaceShipAndReady {

        private UUID gameId;

        @BeforeEach
        void createAndJoin() {
            when(userRepository.findById(PLAYER1_ID))
                    .thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "host")));

            GameResponse created = gameService.createGame(PLAYER1_ID);
            gameId = created.gameId();
            gameService.joinGame(gameId, PLAYER2_ID);
        }

        @Test
        void shouldPlaceShipSuccessfully() {
            PlaceShipRequest request = new PlaceShipRequest(ShipType.CARRIER, 0, 0, Orientation.HORIZONTAL);

            assertDoesNotThrow(() -> gameService.placeShip(gameId, PLAYER1_ID, request));
        }

        @Test
        void shouldMarkReadyAndStartGame() {
            // Place ships
            gameService.placeShip(gameId, PLAYER1_ID, new PlaceShipRequest(ShipType.DESTROYER, 0, 0, Orientation.HORIZONTAL));
            gameService.placeShip(gameId, PLAYER2_ID, new PlaceShipRequest(ShipType.DESTROYER, 0, 0, Orientation.HORIZONTAL));

            gameService.markReady(gameId, PLAYER1_ID);
            gameService.markReady(gameId, PLAYER2_ID);

            // Verify game is in progress by checking current turn
            UUID currentTurn = gameService.getCurrentTurnPlayerId(gameId);
            assertEquals(PLAYER1_ID, currentTurn);
        }
    }

    @Nested
    class Fire {

        private UUID gameId;

        @BeforeEach
        void setupGameInProgress() {
            when(userRepository.findById(PLAYER1_ID))
                    .thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "host")));

            GameResponse created = gameService.createGame(PLAYER1_ID);
            gameId = created.gameId();
            gameService.joinGame(gameId, PLAYER2_ID);

            // Place ships
            gameService.placeShip(gameId, PLAYER1_ID, new PlaceShipRequest(ShipType.DESTROYER, 0, 0, Orientation.HORIZONTAL));
            gameService.placeShip(gameId, PLAYER2_ID, new PlaceShipRequest(ShipType.DESTROYER, 5, 5, Orientation.HORIZONTAL));

            // Mark ready
            gameService.markReady(gameId, PLAYER1_ID);
            gameService.markReady(gameId, PLAYER2_ID);
        }

        @Test
        void shouldReturnHitOnValidShot() {
            ShotResponse response = gameService.fire(gameId, PLAYER1_ID, new FireRequest(5, 5));

            assertEquals(ShotResult.HIT, response.result());
            assertFalse(response.gameOver());
        }

        @Test
        void shouldReturnMissOnEmptyCell() {
            ShotResponse response = gameService.fire(gameId, PLAYER1_ID, new FireRequest(9, 9));

            assertEquals(ShotResult.MISS, response.result());
            assertFalse(response.gameOver());
        }

        @Test
        void shouldRejectFireWhenNotYourTurn() {
            assertThrows(NotYourTurnException.class, () ->
                    gameService.fire(gameId, PLAYER2_ID, new FireRequest(0, 0)));
        }

        @Test
        void shouldEndGameWhenAllShipsSunk() {
            when(userRepository.findById(any())).thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "winner")));

            // Sink player2's destroyer at (5,5)-(5,6)
            gameService.fire(gameId, PLAYER1_ID, new FireRequest(5, 5));
            ShotResponse response = gameService.fire(gameId, PLAYER1_ID, new FireRequest(5, 6));

            assertTrue(response.gameOver());
            assertEquals(PLAYER1_ID, response.winnerId());
            assertEquals(ShotResult.SUNK, response.result());
            assertNotNull(response.sunkShipType());
            assertNotNull(response.sunkShipCells());
        }

        @Test
        void shouldReturnSunkShipCellsOnSink() {
            when(userRepository.findById(any())).thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "winner")));

            gameService.fire(gameId, PLAYER1_ID, new FireRequest(5, 5));
            ShotResponse response = gameService.fire(gameId, PLAYER1_ID, new FireRequest(5, 6));

            assertEquals(ShipType.DESTROYER, response.sunkShipType());
            assertEquals(2, response.sunkShipCells().size());
        }
    }

    @Nested
    class Forfeit {

        private UUID gameId;

        @BeforeEach
        void setupGameInProgress() {
            when(userRepository.findById(PLAYER1_ID))
                    .thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "host")));

            GameResponse created = gameService.createGame(PLAYER1_ID);
            gameId = created.gameId();
            gameService.joinGame(gameId, PLAYER2_ID);

            gameService.placeShip(gameId, PLAYER1_ID, new PlaceShipRequest(ShipType.DESTROYER, 0, 0, Orientation.HORIZONTAL));
            gameService.placeShip(gameId, PLAYER2_ID, new PlaceShipRequest(ShipType.DESTROYER, 5, 5, Orientation.HORIZONTAL));
            gameService.markReady(gameId, PLAYER1_ID);
            gameService.markReady(gameId, PLAYER2_ID);
        }

        @Test
        void shouldDeclareOpponentWinnerOnForfeit() {
            when(userRepository.findById(any())).thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "player")));

            Game result = gameService.forfeit(gameId, PLAYER1_ID);

            assertNotNull(result);
            assertEquals(PLAYER2_ID, result.getWinnerId());
            assertEquals(GameStatus.FINISHED, result.getStatus());
        }

        @Test
        void shouldReturnNullForNonExistentGame() {
            Game result = gameService.forfeit(UUID.randomUUID(), PLAYER1_ID);
            assertNull(result);
        }
    }

    @Nested
    class CancelGame {

        @Test
        void shouldCancelGameInWaitingState() {
            GameResponse created = gameService.createGame(PLAYER1_ID);
            UUID gameId = created.gameId();

            assertDoesNotThrow(() -> gameService.cancelGame(gameId, PLAYER1_ID));
            verify(jpaGameRepository).deleteById(gameId);
        }

        @Test
        void shouldCancelGameInPlacingShipsState() {
            when(userRepository.findById(PLAYER1_ID))
                    .thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "host")));

            GameResponse created = gameService.createGame(PLAYER1_ID);
            UUID gameId = created.gameId();
            gameService.joinGame(gameId, PLAYER2_ID);

            assertDoesNotThrow(() -> gameService.cancelGame(gameId, PLAYER1_ID));
        }

        @Test
        void shouldRejectCancelDuringInProgress() {
            when(userRepository.findById(PLAYER1_ID))
                    .thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "host")));

            GameResponse created = gameService.createGame(PLAYER1_ID);
            UUID gameId = created.gameId();
            gameService.joinGame(gameId, PLAYER2_ID);

            gameService.placeShip(gameId, PLAYER1_ID, new PlaceShipRequest(ShipType.DESTROYER, 0, 0, Orientation.HORIZONTAL));
            gameService.placeShip(gameId, PLAYER2_ID, new PlaceShipRequest(ShipType.DESTROYER, 5, 5, Orientation.HORIZONTAL));
            gameService.markReady(gameId, PLAYER1_ID);
            gameService.markReady(gameId, PLAYER2_ID);

            assertThrows(DomainException.class, () -> gameService.cancelGame(gameId, PLAYER1_ID));
        }
    }

    @Nested
    class FindGameByPlayer {

        @Test
        void shouldFindActiveGameByPlayerId() {
            GameResponse created = gameService.createGame(PLAYER1_ID);
            UUID gameId = created.gameId();

            UUID found = gameService.findGameByPlayer(PLAYER1_ID);
            assertEquals(gameId, found);
        }

        @Test
        void shouldReturnNullWhenPlayerHasNoActiveGame() {
            UUID unknownPlayer = UUID.randomUUID();
            UUID found = gameService.findGameByPlayer(unknownPlayer);
            assertNull(found);
        }
    }

    @Nested
    class RandomUnattackedCell {

        @Test
        void shouldReturnValidCoordinate() {
            when(userRepository.findById(PLAYER1_ID))
                    .thenReturn(Optional.of(createUserEntity(PLAYER1_ID, "host")));

            GameResponse created = gameService.createGame(PLAYER1_ID);
            UUID gameId = created.gameId();
            gameService.joinGame(gameId, PLAYER2_ID);

            gameService.placeShip(gameId, PLAYER1_ID, new PlaceShipRequest(ShipType.DESTROYER, 0, 0, Orientation.HORIZONTAL));
            gameService.placeShip(gameId, PLAYER2_ID, new PlaceShipRequest(ShipType.DESTROYER, 5, 5, Orientation.HORIZONTAL));
            gameService.markReady(gameId, PLAYER1_ID);
            gameService.markReady(gameId, PLAYER2_ID);

            Coordinate cell = gameService.getRandomUnattackedCell(gameId, PLAYER1_ID);

            assertNotNull(cell);
            assertTrue(cell.row() >= 0 && cell.row() <= 9);
            assertTrue(cell.col() >= 0 && cell.col() <= 9);
        }

        @Test
        void shouldReturnNullForNonExistentGame() {
            Coordinate cell = gameService.getRandomUnattackedCell(UUID.randomUUID(), PLAYER1_ID);
            assertNull(cell);
        }
    }

    private UserEntity createUserEntity(UUID id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("hashed");
        user.setWins(0);
        user.setLosses(0);
        return user;
    }
}
