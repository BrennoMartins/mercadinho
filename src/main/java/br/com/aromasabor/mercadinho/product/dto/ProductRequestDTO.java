package br.com.aromasabor.mercadinho.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {

    @NotBlank(message = "não pode estar vazio")
    private String barcode;

    @NotBlank(message = "não pode estar vazio")
    private String name;

    @NotNull(message = "é obrigatório")
    private Long categoryId;

    @NotNull(message = "é obrigatório")
    @PositiveOrZero(message = "deve ser maior ou igual a zero")
    private BigDecimal price;

    @NotNull(message = "é obrigatório")
    @PositiveOrZero(message = "deve ser maior ou igual a zero")
    private Integer stock;
}
