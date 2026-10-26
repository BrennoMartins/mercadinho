package br.com.aromasabor.mercadinho.sale.exception;

public class MarketClosedException extends RuntimeException {

    public MarketClosedException(String message) {
        super(message);
    }
}

