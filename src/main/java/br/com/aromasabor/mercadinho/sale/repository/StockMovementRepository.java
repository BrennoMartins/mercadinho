package br.com.aromasabor.mercadinho.sale.repository;

import br.com.aromasabor.mercadinho.sale.dto.StockMovementResponseDTO;
import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {

    @Query(
            value = """
                    select new br.com.aromasabor.mercadinho.sale.dto.StockMovementResponseDTO(
                        movement.id,
                        product.id,
                        product.name,
                        movement.movementType,
                        movement.quantity,
                        movement.createdAt,
                        movement.reason,
                        movement.discardReason,
                        sale.id
                    )
                    from StockMovementEntity movement
                    join movement.product product
                    left join movement.referenceSale sale
                    where product.id = coalesce(:productId, product.id)
                      and movement.movementType = coalesce(:movementType, movement.movementType)
                      and movement.createdAt >= coalesce(:startDate, movement.createdAt)
                      and movement.createdAt <= coalesce(:endDate, movement.createdAt)
                    order by movement.createdAt desc, movement.id desc
                    """,
            countQuery = """
                    select count(movement)
                    from StockMovementEntity movement
                    where movement.product.id = coalesce(:productId, movement.product.id)
                      and movement.movementType = coalesce(:movementType, movement.movementType)
                      and movement.createdAt >= coalesce(:startDate, movement.createdAt)
                      and movement.createdAt <= coalesce(:endDate, movement.createdAt)
                    """)
    Page<StockMovementResponseDTO> findHistory(
            @Param("productId") Long productId,
            @Param("movementType") String movementType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
