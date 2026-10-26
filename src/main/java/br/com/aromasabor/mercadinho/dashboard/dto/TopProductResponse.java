package br.com.aromasabor.mercadinho.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Produto mais vendido nos últimos sete dias-calendário")
public class TopProductResponse {

    @Schema(description = "Identificador do produto")
    private Long productId;

    @Schema(description = "Nome atual do produto")
    private String productName;

    @Schema(description = "Unidades vendidas em vendas concluídas no período")
    private Long quantitySold;
}
