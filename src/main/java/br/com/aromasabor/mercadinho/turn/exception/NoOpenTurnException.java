package br.com.aromasabor.mercadinho.turn.exception;

public class NoOpenTurnException extends RuntimeException {

    public NoOpenTurnException(String message) {
        super(message);
    }
}

