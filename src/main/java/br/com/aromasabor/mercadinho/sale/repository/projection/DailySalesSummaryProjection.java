package br.com.aromasabor.mercadinho.sale.repository.projection;

import java.math.BigDecimal;

public interface DailySalesSummaryProjection {

    Long getSalesCount();

    BigDecimal getTotalRevenue();
}
