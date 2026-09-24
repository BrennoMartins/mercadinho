package br.com.aromasabor.mercadinho.sale.repository;

import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<SaleEntity, Long> {

	List<SaleEntity> findAllByOrderByCreatedAtDesc();
}


