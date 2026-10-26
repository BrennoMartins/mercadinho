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
@Schema(description = "Produto ativo com estoque baixo")
public class LowStockProductResponse {

    @Schema(description = "Identificador do produto")
    private Long id;

    @Schema(description = "Nome do produto")
    private String name;

    @Schema(description = "Código de barras do produto")
    private String barcode;

    @Schema(description = "Quantidade disponível em estoque")
    private Integer stock;
}
