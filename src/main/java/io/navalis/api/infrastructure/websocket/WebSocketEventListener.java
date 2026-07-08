package io.navalis.api.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        logger.info("Nova conexão WebSocket: {}", event.getUser() != null ? event.getUser().getName() : "anônimo");
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        logger.info("Desconexão WebSocket: {}", event.getUser() != null ? event.getUser().getName() : "anônimo");
    }
}
