package br.com.aromasabor.mercadinho.sale.repository;

import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesSummaryProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.GrossProfitSummaryProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesReportProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.RecentSaleProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.TurnSalesSummaryProjection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<SaleEntity, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<SaleEntity> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<SaleEntity> findWithItemsById(Long id);

    @Query("""
            select count(s.id) as salesCount,
                   coalesce(sum(s.total), 0.00) as totalRevenue
            from SaleEntity s
            where s.turn.id = :turnId
              and s.status = 'COMPLETED'
            """)
    TurnSalesSummaryProjection getSummaryByTurnId(@Param("turnId") UUID turnId);

    @Query("""
            select count(s.id) as salesCount,
                   coalesce(sum(s.total), 0.00) as totalRevenue
            from SaleEntity s
            where s.status = 'COMPLETED'
              and s.createdAt >= :start
              and s.createdAt < :end
            """)
    DailySalesSummaryProjection getDailySummary(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum((item.unitPrice - item.unitCostPrice) * item.quantity), 0.00)
                        as grossProfitKnownAmount,
                   coalesce(sum(case when item.unitCostPrice is null then 1L else 0L end), 0L)
                        as itemsWithoutHistoricalCost
            from SaleItemEntity item
            where item.sale.status = 'COMPLETED'
              and item.sale.createdAt >= :start
              and item.sale.createdAt < :end
            """)
    GrossProfitSummaryProjection getGrossProfitSummary(@Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);

    @Query(value = """
            select cast(s.created_at as date) as saleDate,
                   count(s.id) as salesCount,
                   coalesce(sum(s.total), 0.00) as revenue
            from sales s
            where s.status = 'COMPLETED'
              and s.created_at >= :start
              and s.created_at < :end
            group by cast(s.created_at as date)
            order by cast(s.created_at as date) asc
            """, nativeQuery = true)
    List<DailySalesReportProjection> getDailySalesReport(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            select s.id as id,
                   s.createdAt as createdAt,
                   s.total as total
            from SaleEntity s
            where s.status = 'COMPLETED'
              and s.createdAt >= :start
              and s.createdAt < :end
            order by s.createdAt desc, s.id desc
            """)
    List<RecentSaleProjection> findRecentSales(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               Pageable pageable);
}
