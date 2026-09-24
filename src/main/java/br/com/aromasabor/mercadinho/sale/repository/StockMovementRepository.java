package br.com.aromasabor.mercadinho.sale.repository;

import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {
}

