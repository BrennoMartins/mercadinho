package br.com.aromasabor.mercadinho.product.exception;

public class StockAdjustmentNotNeededException extends RuntimeException {

    public StockAdjustmentNotNeededException(String message) {
        super(message);
    }
}
