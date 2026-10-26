package br.com.aromasabor.mercadinho.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados para ajustar o estoque à quantidade física contada")
public class StockAdjustmentRequestDTO {

    @NotNull(message = "é obrigatório")
    @PositiveOrZero(message = "deve ser maior ou igual a zero")
    @Schema(description = "Quantidade física final que deverá ficar registrada", example = "17")
    private Integer quantityOnHand;

    @NotBlank(message = "não pode estar vazio")
    @Size(max = 255, message = "deve ter no máximo 255 caracteres")
    @Schema(description = "Justificativa obrigatória para o ajuste", example = "Contagem física do estoque")
    private String reason;
}
