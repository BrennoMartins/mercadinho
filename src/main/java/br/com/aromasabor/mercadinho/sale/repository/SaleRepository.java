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

    @Query(value = """
            select case
                       when bool_or(sale_summary.gross_profit_known_amount is null) then null
                       else coalesce(sum(sale_summary.gross_profit_known_amount), 0.00)
                   end as "grossProfitKnownAmount",
                   coalesce(sum(sale_summary.items_without_historical_cost), 0)
                        as "itemsWithoutHistoricalCost"
            from (
                select s.id,
                       case
                           when count(*) filter (where si.unit_cost_price is null) > 0
                                and coalesce(s.discount_amount, 0.00) > 0
                                and bool_or(si.unit_cost_price is not null
                                    and si.discount_allocated_amount is null)
                           then null
                           when count(*) filter (where si.unit_cost_price is null) = 0
                                and not bool_and(si.discount_allocated_amount is not null)
                           then coalesce(sum((si.unit_price - si.unit_cost_price) * si.quantity), 0.00)
                                - coalesce(s.discount_amount, 0.00)
                           else coalesce(sum(case when si.unit_cost_price is not null
                               then (si.unit_price - si.unit_cost_price) * si.quantity
                               else 0.00 end), 0.00)
                                - case when coalesce(s.discount_amount, 0.00) > 0
                                    then coalesce(sum(case when si.unit_cost_price is not null
                                        then si.discount_allocated_amount else 0.00 end), 0.00)
                                    else 0.00
                                  end
                       end as gross_profit_known_amount,
                       count(case when si.unit_cost_price is null then 1 end)
                           as items_without_historical_cost
                from sales s
                join sale_items si on si.sale_id = s.id
                where s.status = 'COMPLETED'
                  and s.created_at >= :start
                  and s.created_at < :end
                group by s.id, s.discount_amount
            ) sale_summary
            """, nativeQuery = true)
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
