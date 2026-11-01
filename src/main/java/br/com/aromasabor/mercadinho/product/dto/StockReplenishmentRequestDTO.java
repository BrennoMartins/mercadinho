package br.com.aromasabor.mercadinho.product.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockReplenishmentRequestDTO {

    @NotNull(message = "é obrigatório")
    @Positive(message = "deve ser maior que zero")
    private Integer quantity;

    @NotNull(message = "é obrigatório")
    @PositiveOrZero(message = "deve ser maior ou igual a zero")
    @Digits(integer = 8, fraction = 2, message = "deve ter no máximo 8 dígitos inteiros e 2 decimais")
    @Schema(description = "Custo unitário de aquisição da quantidade recebida", example = "8.50")
    private BigDecimal unitCostPrice;

    @Size(max = 255, message = "deve ter no máximo 255 caracteres")
    private String reason;
}
