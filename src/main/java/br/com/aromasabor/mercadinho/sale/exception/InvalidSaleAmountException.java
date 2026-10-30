package br.com.aromasabor.mercadinho.sale.exception;

public class InvalidSaleAmountException extends RuntimeException {

    public InvalidSaleAmountException(String message) {
        super(message);
    }
}
