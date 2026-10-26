package br.com.aromasabor.mercadinho.sale.repository;

import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.repository.projection.TurnSalesSummaryProjection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<SaleEntity, Long> {

	List<SaleEntity> findAllByOrderByCreatedAtDesc();

    @Query("""
            select count(s.id) as salesCount,
                   coalesce(sum(s.total), 0.00) as totalRevenue
            from SaleEntity s
            where s.turn.id = :turnId
              and s.status = 'COMPLETED'
            """)
    TurnSalesSummaryProjection getSummaryByTurnId(@Param("turnId") UUID turnId);
}

