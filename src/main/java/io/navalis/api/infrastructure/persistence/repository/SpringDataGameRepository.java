package io.navalis.api.infrastructure.persistence.repository;

import io.navalis.api.infrastructure.persistence.entity.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataGameRepository extends JpaRepository<GameEntity, UUID> {
    List<GameEntity> findByStatus(String status);
}
