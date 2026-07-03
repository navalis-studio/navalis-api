package io.navalis.api.domain.model;

public record Coordinate(int row, int col) {
    public Coordinate {
        if (row < 0 || row > 9) {
            throw new IllegalArgumentException("Linha deve ser entre 0 e 9, recebido: " + row);
        }
        if (col < 0 || col > 9) {
            throw new IllegalArgumentException("Coluna deve ser entre 0 e 9, recebido: " + col);
        }
    }
}
