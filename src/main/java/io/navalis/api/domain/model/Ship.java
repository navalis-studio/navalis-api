package io.navalis.api.domain.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Ship {

    private final ShipType type;
    private final Coordinate start;
    private final Orientation orientation;
    private final Set<Coordinate> hits = new HashSet<>();

    public Ship(ShipType type, Coordinate start, Orientation orientation) {
        this.type = type;
        this.start = start;
        this.orientation = orientation;
    }

    public List<Coordinate> getOccupiedCoordinates() {
        List<Coordinate> coordinates = new ArrayList<>();
        for (int i = 0; i < type.getSize(); i++) {
            if (orientation == Orientation.HORIZONTAL) {
                coordinates.add(new Coordinate(start.row(), start.col() + i));
            } else {
                coordinates.add(new Coordinate(start.row() + i, start.col()));
            }
        }
        return coordinates;
    }

    public void hit(Coordinate coordinate) {
        hits.add(coordinate);
    }

    public boolean isSunk() {
        return hits.size() == type.getSize();
    }

    public ShipType getType() {
        return type;
    }

    public Coordinate getStart() {
        return start;
    }

    public Orientation getOrientation() {
        return orientation;
    }
}
