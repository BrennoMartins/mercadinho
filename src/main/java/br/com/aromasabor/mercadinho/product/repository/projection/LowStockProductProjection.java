package br.com.aromasabor.mercadinho.product.repository.projection;

public interface LowStockProductProjection {

    Long getId();

    String getName();

    String getBarcode();

    Integer getStock();
}
