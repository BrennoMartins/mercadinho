package br.com.aromasabor.mercadinho.product.exception;

public class ProductCostNotDefinedException extends RuntimeException {

    public ProductCostNotDefinedException(String message) {
        super(message);
    }
}
