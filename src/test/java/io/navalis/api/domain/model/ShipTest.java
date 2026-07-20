package io.navalis.api.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipTest {

    @Test
    void shouldCalculateHorizontalOccupiedCoordinates() {
        Ship ship = new Ship(ShipType.CRUISER, new Coordinate(2, 3), Orientation.HORIZONTAL);
        List<Coordinate> coords = ship.getOccupiedCoordinates();

        assertEquals(3, coords.size());
        assertEquals(new Coordinate(2, 3), coords.get(0));
        assertEquals(new Coordinate(2, 4), coords.get(1));
        assertEquals(new Coordinate(2, 5), coords.get(2));
    }

    @Test
    void shouldCalculateVerticalOccupiedCoordinates() {
        Ship ship = new Ship(ShipType.BATTLESHIP, new Coordinate(0, 5), Orientation.VERTICAL);
        List<Coordinate> coords = ship.getOccupiedCoordinates();

        assertEquals(4, coords.size());
        assertEquals(new Coordinate(0, 5), coords.get(0));
        assertEquals(new Coordinate(1, 5), coords.get(1));
        assertEquals(new Coordinate(2, 5), coords.get(2));
        assertEquals(new Coordinate(3, 5), coords.get(3));
    }

    @Test
    void shouldNotBeSunkInitially() {
        Ship ship = new Ship(ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        assertFalse(ship.isSunk());
    }

    @Test
    void shouldNotBeSunkWithPartialHits() {
        Ship ship = new Ship(ShipType.CRUISER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        ship.hit(new Coordinate(0, 0));
        ship.hit(new Coordinate(0, 1));

        assertFalse(ship.isSunk());
    }

    @Test
    void shouldBeSunkWhenAllCellsHit() {
        Ship ship = new Ship(ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        ship.hit(new Coordinate(0, 0));
        ship.hit(new Coordinate(0, 1));

        assertTrue(ship.isSunk());
    }

    @Test
    void shouldIgnoreDuplicateHits() {
        Ship ship = new Ship(ShipType.CARRIER, new Coordinate(0, 0), Orientation.HORIZONTAL);
        ship.hit(new Coordinate(0, 0));
        ship.hit(new Coordinate(0, 0)); // duplicate

        assertFalse(ship.isSunk()); // Carrier has size 5, only 1 unique hit
    }

    @Test
    void shouldOccupyCorrectNumberOfCellsPerType() {
        assertEquals(5, new Ship(ShipType.CARRIER, new Coordinate(0, 0), Orientation.HORIZONTAL).getOccupiedCoordinates().size());
        assertEquals(4, new Ship(ShipType.BATTLESHIP, new Coordinate(0, 0), Orientation.HORIZONTAL).getOccupiedCoordinates().size());
        assertEquals(3, new Ship(ShipType.CRUISER, new Coordinate(0, 0), Orientation.HORIZONTAL).getOccupiedCoordinates().size());
        assertEquals(3, new Ship(ShipType.SUBMARINE, new Coordinate(0, 0), Orientation.HORIZONTAL).getOccupiedCoordinates().size());
        assertEquals(2, new Ship(ShipType.DESTROYER, new Coordinate(0, 0), Orientation.HORIZONTAL).getOccupiedCoordinates().size());
    }
}
