package io.navalis.api.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableScheduling
public class MetricsConfig {

    private static final long ONLINE_TIMEOUT_SECONDS = 60;

    private final AtomicInteger activeGamesGauge = new AtomicInteger(0);
    private final AtomicInteger connectedPlayersGauge = new AtomicInteger(0);
    private final AtomicInteger onlinePlayersGauge = new AtomicInteger(0);
    private final Counter gamesCreatedCounter;
    private final Counter gamesFinishedCounter;
    private final Counter shotsFiredCounter;
    private final Counter shotsHitCounter;
    private final Counter shotsMissCounter;

    // Tracks last activity timestamp per player (HTTP requests)
    private final ConcurrentHashMap<UUID, Instant> playerLastActivity = new ConcurrentHashMap<>();
    // Tracks players with active WebSocket sessions
    private final ConcurrentHashMap<UUID, Boolean> playersWithWebSocket = new ConcurrentHashMap<>();

    public MetricsConfig(MeterRegistry registry) {
        // Gauges - valores atuais
        Gauge.builder("navalis.games.active", activeGamesGauge, AtomicInteger::get)
                .description("Number of active games in memory")
                .register(registry);

        Gauge.builder("navalis.players.connected", connectedPlayersGauge, AtomicInteger::get)
                .description("Number of players in active WebSocket sessions")
                .register(registry);

        Gauge.builder("navalis.players.online", onlinePlayersGauge, AtomicInteger::get)
                .description("Number of players active in the last 60 seconds")
                .register(registry);

        // Counters - totais acumulados
        gamesCreatedCounter = Counter.builder("navalis.games.created")
                .description("Total games created")
                .register(registry);

        gamesFinishedCounter = Counter.builder("navalis.games.finished")
                .description("Total games finished")
                .register(registry);

        shotsFiredCounter = Counter.builder("navalis.shots.fired")
                .description("Total shots fired")
                .register(registry);

        shotsHitCounter = Counter.builder("navalis.shots.hit")
                .description("Total shots that hit a ship")
                .register(registry);

        shotsMissCounter = Counter.builder("navalis.shots.miss")
                .description("Total shots that missed")
                .register(registry);
    }

    public void gameCreated() {
        gamesCreatedCounter.increment();
        activeGamesGauge.incrementAndGet();
    }

    public void gameFinished() {
        gamesFinishedCounter.increment();
        activeGamesGauge.decrementAndGet();
    }

    public void gameRemoved() {
        activeGamesGauge.decrementAndGet();
    }

    public void shotFired(boolean hit) {
        shotsFiredCounter.increment();
        if (hit) {
            shotsHitCounter.increment();
        } else {
            shotsMissCounter.increment();
        }
    }

    public void playerConnected(UUID playerId) {
        connectedPlayersGauge.incrementAndGet();
        playersWithWebSocket.put(playerId, Boolean.TRUE);
    }

    public void playerDisconnected(UUID playerId) {
        connectedPlayersGauge.decrementAndGet();
        playersWithWebSocket.remove(playerId);
    }

    public void playerActivity(UUID playerId) {
        playerLastActivity.put(playerId, Instant.now());
    }

    public void playerLoggedOut(UUID playerId) {
        playerLastActivity.remove(playerId);
        playersWithWebSocket.remove(playerId);
        updateOnlinePlayers();
    }

    @Scheduled(fixedRate = 10000)
    public void updateOnlinePlayers() {
        // Remove inactive HTTP players
        Instant cutoff = Instant.now().minusSeconds(ONLINE_TIMEOUT_SECONDS);
        playerLastActivity.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));

        // Online = players with WebSocket OR recent HTTP activity
        ConcurrentHashMap<UUID, Boolean> allOnline = new ConcurrentHashMap<>(playersWithWebSocket);
        playerLastActivity.keySet().forEach(id -> allOnline.put(id, Boolean.TRUE));
        onlinePlayersGauge.set(allOnline.size());
    }

}
