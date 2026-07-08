package io.navalis.api.infrastructure.persistence.repository;

import io.navalis.api.infrastructure.persistence.entity.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataGameRepository extends JpaRepository<GameEntity, UUID> {
    List<GameEntity> findByStatus(String status);

    @Modifying
    @Transactional
    @Query("DELETE FROM GameEntity g WHERE g.status IN :statuses")
    void deleteByStatusIn(List<String> statuses);

    @Modifying
    @Transactional
    @Query("DELETE FROM GameEntity g WHERE g.id = :id")
    void deleteGameById(UUID id);
}
