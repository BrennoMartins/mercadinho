package br.com.aromasabor.mercadinho.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
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

    @Positive(message = "deve ser maior que zero")
    @Digits(integer = 8, fraction = 2, message = "deve ter no máximo 2 casas decimais")
    @Schema(description = "Preço unitário negociado para este item. Quando omitido, usa o preço cadastrado do produto.",
            example = "11.90")
    private BigDecimal unitPrice;

    public SaleItemRequestDTO(Long productId, Integer quantity) {
        this(productId, quantity, null);
    }
}

