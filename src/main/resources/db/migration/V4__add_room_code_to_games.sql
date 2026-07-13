ALTER TABLE games ADD COLUMN room_code VARCHAR(6) NOT NULL DEFAULT '';
CREATE UNIQUE INDEX idx_games_room_code ON games(room_code) WHERE status = 'WAITING_FOR_OPPONENT' OR status = 'PLACING_SHIPS' OR status = 'IN_PROGRESS';
