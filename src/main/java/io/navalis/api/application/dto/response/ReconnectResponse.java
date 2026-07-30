package io.navalis.api.application.dto.response;

import io.navalis.api.domain.model.GameStatus;

import java.util.List;
import java.util.UUID;

public record ReconnectResponse(
        UUID gameId,
        String roomCode,
        GameStatus status,
        boolean myTurn,
        List<ShipData> myShips,
        List<ShotData> myShots,
        List<ShotData> enemyShots,
        List<String> sunkEnemyShips,
        List<String> sunkMyShips,
        boolean opponentReady,
        boolean myReady,
        String opponentUsername
) {
    public record ShipData(String shipType, int row, int col, String orientation) {}
    public record ShotData(int row, int col, String result) {}
}
