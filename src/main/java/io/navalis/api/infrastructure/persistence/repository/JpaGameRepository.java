package io.navalis.api.infrastructure.persistence.repository;

import io.navalis.api.domain.model.Game;
import io.navalis.api.domain.port.GameRepository;
import io.navalis.api.infrastructure.persistence.entity.GameEntity;
import io.navalis.api.infrastructure.persistence.mapper.GameMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaGameRepository implements GameRepository {

    private final SpringDataGameRepository springDataRepo;
    private final GameMapper gameMapper;

    public JpaGameRepository(SpringDataGameRepository springDataRepo, GameMapper gameMapper) {
        this.springDataRepo = springDataRepo;
        this.gameMapper = gameMapper;
    }

    @Override
    public Optional<Game> findById(UUID id) {
        return Optional.empty();
    }

    @Override
    public void save(Game game) {
        Optional<GameEntity> existing = springDataRepo.findById(game.getId());
        if (existing.isPresent()) {
            GameEntity entity = gameMapper.updateEntity(existing.get(), game);
            entity.markAsExisting();
            springDataRepo.save(entity);
        } else {
            GameEntity entity = gameMapper.toEntity(game);
            springDataRepo.save(entity);
        }
    }

    public List<GameEntity> findByStatus(String status) {
        return springDataRepo.findByStatus(status);
    }

    public void deleteByStatusIn(List<String> statuses) {
        springDataRepo.deleteByStatusIn(statuses);
    }

    public void deleteById(UUID id) {
        springDataRepo.deleteById(id);
    }
}
