package br.com.aromasabor.mercadinho.sale.exception;

public class SaleWithoutItemsException extends RuntimeException {

    public SaleWithoutItemsException(String message) {
        super(message);
    }
}

