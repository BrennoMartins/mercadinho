package br.com.aromasabor.mercadinho.sale.repository.projection;

import java.math.BigDecimal;

public interface TurnSalesSummaryProjection {

    Long getSalesCount();

    BigDecimal getTotalRevenue();
}
