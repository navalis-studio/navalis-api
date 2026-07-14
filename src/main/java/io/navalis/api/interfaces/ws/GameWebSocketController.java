package io.navalis.api.interfaces.ws;

import io.navalis.api.application.dto.request.FireRequest;
import io.navalis.api.application.dto.request.PlaceShipRequest;
import io.navalis.api.application.dto.response.GameResponse;
import io.navalis.api.application.dto.response.ShotResponse;
import io.navalis.api.application.service.GameService;
import io.navalis.api.domain.model.GameStatus;
import io.navalis.api.infrastructure.websocket.StompPrincipal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class GameWebSocketController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/game/{gameId}/place-ship")
    public void placeShip(@DestinationVariable UUID gameId,
                          @Payload PlaceShipRequest request,
                          Principal principal) {
        UUID playerId = extractUserId(principal);
        gameService.placeShip(gameId, playerId, request);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "SHIP_PLACED");
        notification.put("playerId", playerId.toString());

        messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);
    }

    @MessageMapping("/game/{gameId}/ready")
    public void markReady(@DestinationVariable UUID gameId, Principal principal) {
        UUID playerId = extractUserId(principal);
        gameService.markReady(gameId, playerId);

        GameResponse gameInfo = gameService.getGameInfo(gameId);
        Map<String, Object> notification = new HashMap<>();

        if (gameInfo.status() == GameStatus.IN_PROGRESS) {
            notification.put("type", "GAME_STARTED");
            notification.put("message", "Ambos prontos. Partida iniciada!");
            notification.put("firstPlayerId", gameService.getCurrentTurnPlayerId(gameId).toString());
        } else {
            notification.put("type", "PLAYER_READY");
            notification.put("playerId", playerId.toString());
        }

        messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);
    }

    @MessageMapping("/game/{gameId}/fire")
    public void fire(@DestinationVariable UUID gameId,
                     @Payload FireRequest request,
                     Principal principal) {
        UUID playerId = extractUserId(principal);
        ShotResponse response = gameService.fire(gameId, playerId, request);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "SHOT_FIRED");
        notification.put("shooterId", playerId.toString());
        notification.put("row", request.row());
        notification.put("col", request.col());
        notification.put("result", response.result().name());
        notification.put("sunkShipType", response.sunkShipType() != null ? response.sunkShipType().name() : null);
        notification.put("sunkShipCells", response.sunkShipCells());
        notification.put("gameOver", response.gameOver());
        notification.put("winnerId", response.winnerId() != null ? response.winnerId().toString() : null);

        messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) {
            return stompPrincipal.getUserId();
        }
        return UUID.fromString(principal.getName());
    }
}
