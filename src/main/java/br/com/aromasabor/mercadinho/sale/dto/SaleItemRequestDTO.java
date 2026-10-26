package br.com.aromasabor.mercadinho.sale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaleItemRequestDTO {

    @NotNull(message = "é obrigatório")
    @Positive(message = "deve ser maior que zero")
    private Long productId;

    @NotNull(message = "é obrigatório")
    @Positive(message = "deve ser maior que zero")
    private Integer quantity;
}

