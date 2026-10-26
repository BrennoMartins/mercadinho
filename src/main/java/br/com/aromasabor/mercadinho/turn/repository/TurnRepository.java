package br.com.aromasabor.mercadinho.turn.repository;

import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnRepository extends JpaRepository<TurnEntity, UUID> {

    boolean existsByStatus(TurnStatus status);

    Optional<TurnEntity> findFirstByStatusOrderByOpenedAtDesc(TurnStatus status);

    Optional<TurnEntity> findByIdAndStatus(UUID id, TurnStatus status);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select turn
            from TurnEntity turn
            where turn.status = :status
            order by turn.openedAt desc
            """)
    Optional<TurnEntity> findFirstByStatusForShare(@Param("status") TurnStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select turn
            from TurnEntity turn
            where turn.id = :id and turn.status = :status
            """)
    Optional<TurnEntity> findByIdAndStatusForUpdate(@Param("id") UUID id,
                                                    @Param("status") TurnStatus status);

    List<TurnEntity> findAllByOrderByOpenedAtDesc();
}
