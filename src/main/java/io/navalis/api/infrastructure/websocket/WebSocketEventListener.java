package io.navalis.api.infrastructure.websocket;

import io.navalis.api.application.service.GameService;
import io.navalis.api.domain.model.Game;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    private static final int RECONNECT_TIMEOUT_SECONDS = 30;

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Tracks pending disconnect timers: playerId -> scheduledFuture
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();
    // Tracks which game a disconnected player was in: playerId -> gameId
    private final ConcurrentHashMap<UUID, UUID> disconnectedPlayers = new ConcurrentHashMap<>();

    public WebSocketEventListener(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        if (event.getUser() == null) return;

        String userIdStr = event.getUser().getName();
        logger.info("Nova conexão WebSocket: {}", userIdStr);

        try {
            UUID playerId = UUID.fromString(userIdStr);

            // Check if this player has a pending disconnect timer
            ScheduledFuture<?> pendingTimer = pendingDisconnects.remove(playerId);
            if (pendingTimer != null) {
                // Cancel the timer - player reconnected in time!
                pendingTimer.cancel(false);
                UUID gameId = disconnectedPlayers.remove(playerId);

                if (gameId != null) {
                    logger.info("Jogador {} reconectou à partida {} dentro do tempo limite", playerId, gameId);

                    // Notify the opponent that player reconnected
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", "OPPONENT_RECONNECTED");
                    notification.put("playerId", playerId.toString());
                    messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);
                }
            }
        } catch (Exception e) {
            logger.error("Erro ao processar reconexão: {}", e.getMessage());
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() == null) return;

        String userIdStr = event.getUser().getName();
        logger.info("Desconexão WebSocket: {}", userIdStr);

        try {
            UUID playerId = UUID.fromString(userIdStr);
            UUID gameId = gameService.findGameByPlayer(playerId);

            if (gameId == null) return;

            // Store the disconnected player's game
            disconnectedPlayers.put(playerId, gameId);

            // Notify opponent that player temporarily disconnected
            Map<String, Object> tempNotification = new HashMap<>();
            tempNotification.put("type", "OPPONENT_DISCONNECTED_TEMP");
            tempNotification.put("playerId", playerId.toString());
            tempNotification.put("timeoutSeconds", RECONNECT_TIMEOUT_SECONDS);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) tempNotification);

            logger.info("Jogador {} desconectou da partida {}. Aguardando reconexão ({}s)...", playerId, gameId, RECONNECT_TIMEOUT_SECONDS);

            // Schedule the actual disconnect processing after 30 seconds
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                executeDisconnect(playerId, gameId);
            }, RECONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            pendingDisconnects.put(playerId, future);

        } catch (Exception e) {
            logger.error("Erro ao processar desconexão: {}", e.getMessage());
        }
    }

    private void executeDisconnect(UUID playerId, UUID gameId) {
        // Remove from tracking maps
        pendingDisconnects.remove(playerId);
        disconnectedPlayers.remove(playerId);

        logger.info("Timeout de reconexão expirado para jogador {} na partida {}", playerId, gameId);

        try {
            Game game = gameService.forfeit(gameId, playerId);

            if (game != null) {
                // WO: game was IN_PROGRESS, remaining player wins
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "OPPONENT_DISCONNECTED");
                notification.put("quitterId", playerId.toString());
                notification.put("winnerId", game.getWinnerId().toString());
                notification.put("gameOver", true);
                messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);

                logger.info("WO: jogador {} abandonou partida {}. Vencedor: {}", playerId, gameId, game.getWinnerId());
            } else {
                // Game was cancelled (WAITING or PLACING_SHIPS) or already finished/removed
                // Only send notification if game still existed
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "GAME_CANCELLED");
                notification.put("quitterId", playerId.toString());
                messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);

                logger.info("Partida {} cancelada pelo jogador {} (timeout de reconexão)", gameId, playerId);
            }
        } catch (Exception e) {
            logger.error("Erro ao executar forfeit após timeout: {}", e.getMessage());
        }
    }
}
