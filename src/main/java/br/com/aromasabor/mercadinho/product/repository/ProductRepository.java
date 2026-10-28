package br.com.aromasabor.mercadinho.product.repository;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.projection.LowStockProductProjection;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    List<ProductEntity> findByActiveTrue();

    List<ProductEntity> findByActiveFalseOrderByIdAsc();

    List<ProductEntity> findTop10ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAscIdAsc(String name);

    @Query("""
            select p.id as id,
                   p.name as name,
                   p.barcode as barcode,
                   p.stock as stock
            from ProductEntity p
            where p.active = true
              and p.stock <= :stockThreshold
            order by p.stock asc, p.name asc, p.id asc
            """)
    List<LowStockProductProjection> findLowStockProducts(@Param("stockThreshold") Integer stockThreshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from ProductEntity p
            where p.id in :ids
            """)
    List<ProductEntity> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from ProductEntity p
            where p.id = :id
            """)
    Optional<ProductEntity> findByIdForUpdate(@Param("id") Long id);

    long countByActiveTrueAndStockLessThanEqual(Integer stock);
}
