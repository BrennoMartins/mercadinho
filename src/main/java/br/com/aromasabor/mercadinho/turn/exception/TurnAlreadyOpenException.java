package br.com.aromasabor.mercadinho.turn.exception;

public class TurnAlreadyOpenException extends RuntimeException {

    public TurnAlreadyOpenException(String message) {
        super(message);
    }
}

