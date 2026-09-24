package br.com.aromasabor.mercadinho.turn.repository;

import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnRepository extends JpaRepository<TurnEntity, UUID> {

    boolean existsByStatus(TurnStatus status);

    Optional<TurnEntity> findFirstByStatusOrderByOpenedAtDesc(TurnStatus status);

    Optional<TurnEntity> findByIdAndStatus(UUID id, TurnStatus status);

    List<TurnEntity> findAllByOrderByOpenedAtDesc();
}

