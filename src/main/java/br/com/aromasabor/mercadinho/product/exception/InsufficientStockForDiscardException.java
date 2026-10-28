package br.com.aromasabor.mercadinho.product.exception;

public class InsufficientStockForDiscardException extends RuntimeException {

    public InsufficientStockForDiscardException(String message) {
        super(message);
    }
}
