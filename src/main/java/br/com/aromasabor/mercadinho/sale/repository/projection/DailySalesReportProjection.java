package br.com.aromasabor.mercadinho.sale.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailySalesReportProjection {

    LocalDate getSaleDate();

    Long getSalesCount();

    BigDecimal getRevenue();
}
