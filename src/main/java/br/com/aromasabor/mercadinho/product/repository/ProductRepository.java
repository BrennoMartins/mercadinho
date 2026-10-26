package br.com.aromasabor.mercadinho.product.repository;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
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

    List<ProductEntity> findByNameContainingIgnoreCase(String name);

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
