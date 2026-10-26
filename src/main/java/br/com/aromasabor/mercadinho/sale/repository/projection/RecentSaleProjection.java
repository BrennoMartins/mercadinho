package br.com.aromasabor.mercadinho.sale.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface RecentSaleProjection {

    Long getId();

    LocalDateTime getCreatedAt();

    BigDecimal getTotal();
}
