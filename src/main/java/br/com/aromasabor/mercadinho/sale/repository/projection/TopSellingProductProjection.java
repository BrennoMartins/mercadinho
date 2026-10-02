package br.com.aromasabor.mercadinho.sale.repository.projection;

import java.math.BigDecimal;

public interface TopSellingProductProjection {

    Long getProductId();

    String getProductName();

    Long getQuantitySold();

    BigDecimal getRevenue();
}
