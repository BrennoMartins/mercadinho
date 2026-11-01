package br.com.aromasabor.mercadinho.product.exception;

public class CategoryHasProductsException extends RuntimeException {

    public CategoryHasProductsException() {
        super("Esta categoria possui produtos vinculados e não pode ser excluída.");
    }
}
