package io.navalis.api.interfaces.rest;

import io.navalis.api.application.dto.response.GameResponse;
import io.navalis.api.application.dto.response.ReconnectResponse;
import io.navalis.api.application.service.GameService;
import io.navalis.api.infrastructure.persistence.entity.UserEntity;
import io.navalis.api.infrastructure.persistence.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public GameController(GameService gameService, SimpMessagingTemplate messagingTemplate, UserRepository userRepository) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<GameResponse> createGame(Principal principal) {
        UUID playerId = UUID.fromString(principal.getName());
        GameResponse response = gameService.createGame(playerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{gameId}/join")
    public ResponseEntity<GameResponse> joinGame(@PathVariable UUID gameId, Principal principal) {
        UUID playerId = UUID.fromString(principal.getName());
        GameResponse response = gameService.joinGame(gameId, playerId);

        notifyOpponentJoined(gameId, playerId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/join/{roomCode}")
    public ResponseEntity<GameResponse> joinByRoomCode(@PathVariable String roomCode, Principal principal) {
        UUID playerId = UUID.fromString(principal.getName());
        GameResponse response = gameService.joinByRoomCode(roomCode.toUpperCase(), playerId);

        notifyOpponentJoined(response.gameId(), playerId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/available")
    public ResponseEntity<List<GameResponse>> findAvailableGames() {
        List<GameResponse> games = gameService.findAvailableGames();
        return ResponseEntity.ok(games);
    }

    @GetMapping("/active")
    public ResponseEntity<ReconnectResponse> getActiveGame(Principal principal) {
        UUID playerId = UUID.fromString(principal.getName());
        ReconnectResponse response = gameService.getReconnectData(playerId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<GameResponse> getGameInfo(@PathVariable UUID gameId) {
        GameResponse response = gameService.getGameInfo(gameId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{gameId}")
    public ResponseEntity<Void> cancelGame(@PathVariable UUID gameId, Principal principal) {
        UUID playerId = UUID.fromString(principal.getName());

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "GAME_CANCELLED");
        notification.put("quitterId", playerId.toString());
        messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);

        gameService.cancelGame(gameId, playerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{gameId}/forfeit")
    public ResponseEntity<Void> forfeitGame(@PathVariable UUID gameId, Principal principal) {
        UUID playerId = UUID.fromString(principal.getName());

        var game = gameService.forfeit(gameId, playerId);

        if (game != null) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "OPPONENT_DISCONNECTED");
            notification.put("quitterId", playerId.toString());
            notification.put("winnerId", game.getWinnerId().toString());
            notification.put("gameOver", true);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);
        }

        return ResponseEntity.noContent().build();
    }

    private void notifyOpponentJoined(UUID gameId, UUID joiningPlayerId) {
        String joinerUsername = userRepository.findById(joiningPlayerId)
                .map(UserEntity::getUsername).orElse("Oponente");
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "OPPONENT_JOINED");
        notification.put("playerId", joiningPlayerId.toString());
        notification.put("username", joinerUsername);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);
    }
}
