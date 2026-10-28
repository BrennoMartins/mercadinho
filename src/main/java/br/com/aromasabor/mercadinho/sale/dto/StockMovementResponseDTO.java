package br.com.aromasabor.mercadinho.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Movimentação registrada no histórico de estoque")
public class StockMovementResponseDTO {

    @Schema(description = "Identificador da movimentação", example = "42")
    private final Long id;

    @Schema(description = "Identificador do produto", example = "7")
    private final Long productId;

    @Schema(description = "Nome do produto", example = "Arroz")
    private final String productName;

    @Schema(description = "Tipo da movimentação", allowableValues = {"SALE", "ENTRY", "ADJUSTMENT", "DISCARD"})
    private final String movementType;

    @Schema(description = "Quantidade movimentada; saídas de venda são negativas", example = "-2")
    private final Integer quantity;

    @Schema(description = "Data e hora da movimentação", example = "2026-10-25T18:30:00")
    private final LocalDateTime createdAt;

    @Schema(description = "Motivo ou observação registrada", example = "Venda #15")
    private final String reason;

    @Schema(description = "Motivo categorizado do descarte", example = "DAMAGED", nullable = true)
    private final String discardReason;

    @Schema(description = "Identificador da venda relacionada, quando aplicável", example = "15", nullable = true)
    private final Long referenceSaleId;
}
