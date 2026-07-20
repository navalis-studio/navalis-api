package io.navalis.api.domain.model;

import io.navalis.api.domain.exception.InvalidPlacementException;
import io.navalis.api.domain.exception.InvalidShotException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    private Board board;

    @BeforeEach
    void setUp() {
        board = new Board();
    }

    @Test
    void shouldPlaceShipSuccessfully() {
        Ship ship = new Ship(ShipType.CRUISER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        board.placeShip(ship);

        assertEquals(1, board.getShips().size());
    }

    @Test
    void shouldRejectOverlappingShips() {
        Ship ship1 = new Ship(ShipType.CRUISER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        Ship ship2 = new Ship(ShipType.DESTROYER, new Coordinate(0, 1), Orientation.VERTICAL);

        board.placeShip(ship1);
        assertThrows(InvalidPlacementException.class, () -> board.placeShip(ship2));
    }

    @Test
    void shouldAllowNonOverlappingShips() {
        Ship ship1 = new Ship(ShipType.CRUISER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        Ship ship2 = new Ship(ShipType.DESTROYER, new Coordinate(2, 0), Orientation.HORIZONTAL);

        board.placeShip(ship1);
        assertDoesNotThrow(() -> board.placeShip(ship2));
        assertEquals(2, board.getShips().size());
    }

    @Test
    void shouldReturnHitWhenShotHitsShip() {
        Ship ship = new Ship(ShipType.DESTROYER, new Coordinate(3, 3), Orientation.HORIZONTAL);
        board.placeShip(ship);

        ShotResult result = board.receiveShot(new Coordinate(3, 3));
        assertEquals(ShotResult.HIT, result);
    }

    @Test
    void shouldReturnMissWhenShotMissesAllShips() {
        Ship ship = new Ship(ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        board.placeShip(ship);

        ShotResult result = board.receiveShot(new Coordinate(5, 5));
        assertEquals(ShotResult.MISS, result);
    }

    @Test
    void shouldReturnSunkWhenLastCellOfShipIsHit() {
        Ship ship = new Ship(ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        board.placeShip(ship);

        board.receiveShot(new Coordinate(0, 0));
        ShotResult result = board.receiveShot(new Coordinate(0, 1));

        assertEquals(ShotResult.SUNK, result);
    }

    @Test
    void shouldRejectDuplicateShot() {
        Ship ship = new Ship(ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        board.placeShip(ship);

        board.receiveShot(new Coordinate(0, 0));
        assertThrows(InvalidShotException.class, () -> board.receiveShot(new Coordinate(0, 0)));
    }

    @Test
    void shouldRejectDuplicateMissShot() {
        board.placeShip(new Ship(ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL));

        board.receiveShot(new Coordinate(5, 5)); // miss
        assertThrows(InvalidShotException.class, () -> board.receiveShot(new Coordinate(5, 5)));
    }

    @Test
    void shouldReportAllShipsSunkWhenFleetDestroyed() {
        Ship destroyer = new Ship(ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        Ship submarine = new Ship(ShipType.SUBMARINE, new Coordinate(2, 0), Orientation.HORIZONTAL);
        board.placeShip(destroyer);
        board.placeShip(submarine);

        // Sink destroyer
        board.receiveShot(new Coordinate(0, 0));
        board.receiveShot(new Coordinate(0, 1));
        assertFalse(board.allShipsSunk());

        // Sink submarine
        board.receiveShot(new Coordinate(2, 0));
        board.receiveShot(new Coordinate(2, 1));
        board.receiveShot(new Coordinate(2, 2));
        assertTrue(board.allShipsSunk());
    }

    @Test
    void shouldNotReportAllShipsSunkOnEmptyBoard() {
        assertFalse(board.allShipsSunk());
    }

    @Test
    void shouldTrackShotStatesCorrectly() {
        board.placeShip(new Ship(ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL));

        board.receiveShot(new Coordinate(0, 0)); // hit
        board.receiveShot(new Coordinate(5, 5)); // miss

        assertEquals(CellState.HIT, board.getShots().get(new Coordinate(0, 0)));
        assertEquals(CellState.MISS, board.getShots().get(new Coordinate(5, 5)));
    }
}
