package br.com.aromasabor.mercadinho.sale.repository.projection;

public interface DashboardTopProductProjection {

    Long getProductId();

    String getProductName();

    Long getQuantitySold();
}
