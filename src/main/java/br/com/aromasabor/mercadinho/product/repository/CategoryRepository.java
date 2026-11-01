package br.com.aromasabor.mercadinho.product.repository;

import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    Optional<CategoryEntity> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select category from CategoryEntity category where category.id = :id")
    Optional<CategoryEntity> findByIdForUpdate(@Param("id") Long id);
}
