package br.com.aromasabor.mercadinho.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados para registrar o descarte de produtos")
public class StockDiscardRequestDTO {

    @NotNull(message = "é obrigatório")
    @Positive(message = "deve ser maior que zero")
    @Schema(description = "Quantidade inteira a descartar", example = "2")
    private Integer quantity;

    @NotNull(message = "é obrigatório")
    @Schema(description = "Motivo do descarte", example = "DAMAGED")
    private StockDiscardReason reason;

    @Size(max = 255, message = "deve ter no máximo 255 caracteres")
    @Schema(description = "Observação opcional sobre o descarte", example = "Embalagem violada")
    private String observation;
}
