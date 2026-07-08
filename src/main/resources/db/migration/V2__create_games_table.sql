CREATE TABLE games (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player1_id UUID NOT NULL REFERENCES users(id),
    player2_id UUID REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING_FOR_OPPONENT',
    current_turn_player_id UUID,
    winner_id UUID REFERENCES users(id),
    game_state JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP
);

CREATE INDEX idx_games_status ON games(status);
CREATE INDEX idx_games_player1 ON games(player1_id);
CREATE INDEX idx_games_player2 ON games(player2_id);
