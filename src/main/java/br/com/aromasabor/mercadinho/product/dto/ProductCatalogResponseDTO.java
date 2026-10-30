package br.com.aromasabor.mercadinho.product.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductCatalogResponseDTO {

    private List<ProductResponseDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
