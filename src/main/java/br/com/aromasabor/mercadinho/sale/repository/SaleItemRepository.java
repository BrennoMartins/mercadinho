package br.com.aromasabor.mercadinho.sale.repository;

import br.com.aromasabor.mercadinho.sale.entity.SaleItemEntity;
import br.com.aromasabor.mercadinho.sale.repository.projection.TopSellingProductProjection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleItemRepository extends JpaRepository<SaleItemEntity, Long> {

    @Query("""
            select item.product.id as productId,
                   item.product.name as productName,
                   sum(item.quantity) as quantitySold,
                   sum(item.subtotal) as revenue
            from SaleItemEntity item
            where item.sale.turn.id = :turnId
              and item.sale.status = 'COMPLETED'
            group by item.product.id, item.product.name
            order by sum(item.quantity) desc, sum(item.subtotal) desc, item.product.name asc
            """)
    List<TopSellingProductProjection> findTopSellingProductsByTurnId(
            @Param("turnId") UUID turnId,
            Pageable pageable);
}
