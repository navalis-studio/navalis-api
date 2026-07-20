package io.navalis.api.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateTest {

    @Test
    void shouldCreateValidCoordinate() {
        Coordinate coord = new Coordinate(0, 0);
        assertEquals(0, coord.row());
        assertEquals(0, coord.col());
    }

    @Test
    void shouldAcceptBoundaryValues() {
        assertDoesNotThrow(() -> new Coordinate(0, 0));
        assertDoesNotThrow(() -> new Coordinate(9, 9));
        assertDoesNotThrow(() -> new Coordinate(0, 9));
        assertDoesNotThrow(() -> new Coordinate(9, 0));
    }

    @ParameterizedTest
    @CsvSource({"-1, 0", "10, 0", "0, -1", "0, 10", "-1, -1", "10, 10"})
    void shouldRejectInvalidCoordinates(int row, int col) {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(row, col));
    }

    @Test
    void shouldBeEqualWhenSameValues() {
        Coordinate a = new Coordinate(3, 5);
        Coordinate b = new Coordinate(3, 5);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenDifferentValues() {
        Coordinate a = new Coordinate(3, 5);
        Coordinate b = new Coordinate(5, 3);
        assertNotEquals(a, b);
    }
}
