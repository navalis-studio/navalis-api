package io.navalis.api.interfaces.rest;

import io.navalis.api.application.dto.response.GameResponse;
import io.navalis.api.application.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
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
        return ResponseEntity.ok(response);
    }

    @GetMapping("/available")
    public ResponseEntity<List<GameResponse>> findAvailableGames() {
        List<GameResponse> games = gameService.findAvailableGames();
        return ResponseEntity.ok(games);
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<GameResponse> getGameInfo(@PathVariable UUID gameId) {
        GameResponse response = gameService.getGameInfo(gameId);
        return ResponseEntity.ok(response);
    }
}
