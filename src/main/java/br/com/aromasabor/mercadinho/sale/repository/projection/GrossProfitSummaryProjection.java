package br.com.aromasabor.mercadinho.sale.repository.projection;

import java.math.BigDecimal;

public interface GrossProfitSummaryProjection {

    BigDecimal getGrossProfitKnownAmount();

    Long getItemsWithoutHistoricalCost();
}
