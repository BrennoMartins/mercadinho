package br.com.aromasabor.mercadinho.product.repository;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    List<ProductEntity> findByActiveTrue();

    List<ProductEntity> findByNameContainingIgnoreCase(String name);
}

