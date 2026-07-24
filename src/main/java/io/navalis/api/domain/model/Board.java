package io.navalis.api.domain.model;

import io.navalis.api.domain.exception.InvalidPlacementException;
import io.navalis.api.domain.exception.InvalidShotException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Board {

    private final List<Ship> ships = new ArrayList<>();
    private final Map<Coordinate, CellState> shots = new HashMap<>();

    public void placeShip(Ship ship) {
        // Prevent duplicate ship types
        boolean alreadyPlaced = ships.stream()
                .anyMatch(s -> s.getType() == ship.getType());
        if (alreadyPlaced) {
            throw new InvalidPlacementException("error.ship_already_placed");
        }

        List<Coordinate> newCoordinates = ship.getOccupiedCoordinates();

        for (Coordinate coordinate : newCoordinates) {
            if (isOccupied(coordinate)) {
                throw new InvalidPlacementException("error.position_occupied");
            }
        }

        ships.add(ship);
    }

    public ShotResult receiveShot(Coordinate target) {
        if (shots.containsKey(target)) {
            throw new InvalidShotException("error.already_shot");
        }

        for (Ship ship : ships) {
            if (ship.getOccupiedCoordinates().contains(target)) {
                ship.hit(target);
                shots.put(target, CellState.HIT);

                if (ship.isSunk()) {
                    return ShotResult.SUNK;
                }
                return ShotResult.HIT;
            }
        }

        shots.put(target, CellState.MISS);
        return ShotResult.MISS;
    }

    public boolean allShipsSunk() {
        return !ships.isEmpty() && ships.stream().allMatch(Ship::isSunk);
    }

    private boolean isOccupied(Coordinate coordinate) {
        for (Ship ship : ships) {
            if (ship.getOccupiedCoordinates().contains(coordinate)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        ships.clear();
        shots.clear();
    }

    public List<Ship> getShips() {
        return ships;
    }

    public Map<Coordinate, CellState> getShots() {
        return shots;
    }
}
