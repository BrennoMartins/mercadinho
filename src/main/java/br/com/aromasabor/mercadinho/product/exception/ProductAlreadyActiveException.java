package br.com.aromasabor.mercadinho.product.exception;

public class ProductAlreadyActiveException extends RuntimeException {

    public ProductAlreadyActiveException(String message) {
        super(message);
    }
}

