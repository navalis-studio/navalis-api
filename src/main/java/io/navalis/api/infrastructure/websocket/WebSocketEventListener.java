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

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventListener(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        logger.info("Nova conexão WebSocket: {}", event.getUser() != null ? event.getUser().getName() : "anônimo");
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

            Game game = gameService.forfeit(gameId, playerId);

            if (game != null) {
                // Notify the remaining player of the WO victory
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "OPPONENT_DISCONNECTED");
                notification.put("quitterId", playerId.toString());
                notification.put("winnerId", game.getWinnerId().toString());
                notification.put("gameOver", true);
                messagingTemplate.convertAndSend("/topic/game/" + gameId, (Object) notification);

                logger.info("WO: jogador {} abandonou partida {}. Vencedor: {}", playerId, gameId, game.getWinnerId());
            }
        } catch (Exception e) {
            logger.error("Erro ao processar desconexão: {}", e.getMessage());
        }
    }
}
