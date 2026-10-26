package br.com.aromasabor.mercadinho.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados resumidos de uma venda concluída")
public class RecentSaleResponse {

    @Schema(description = "Identificador da venda")
    private Long id;

    @Schema(description = "Data e hora local da venda")
    private LocalDateTime createdAt;

    @Schema(description = "Total persistido da venda")
    private BigDecimal total;
}
