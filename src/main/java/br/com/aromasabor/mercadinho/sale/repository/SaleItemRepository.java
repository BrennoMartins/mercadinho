package br.com.aromasabor.mercadinho.sale.repository;

import br.com.aromasabor.mercadinho.sale.entity.SaleItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItemEntity, Long> {
}

