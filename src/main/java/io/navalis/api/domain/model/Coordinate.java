package io.navalis.api.domain.model;

public record Coordinate(int row, int col) {
    public Coordinate {
        if (row < 0 || row > 9) {
            throw new IllegalArgumentException("error.invalid_row");
        }
        if (col < 0 || col > 9) {
            throw new IllegalArgumentException("error.invalid_col");
        }
    }
}
