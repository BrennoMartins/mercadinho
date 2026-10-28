package br.com.aromasabor.mercadinho.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Indicadores operacionais do dia atual")
public class DashboardSummaryResponse {

    @Schema(description = "Data local do resumo")
    private LocalDate date;

    @Schema(description = "Faturamento das vendas concluídas no dia")
    private BigDecimal revenue;

    @Schema(description = "Quantidade de vendas concluídas no dia")
    private Long salesCount;

    @Schema(description = "Ticket médio das vendas concluídas no dia")
    private BigDecimal averageTicket;

    @Schema(description = "Lucro bruto calculado apenas para itens com custo histórico conhecido")
    private BigDecimal grossProfitKnownAmount;

    @Schema(description = "Indica se todos os itens concluídos do dia possuem custo histórico")
    private Boolean grossProfitComplete;

    @Schema(description = "Quantidade de itens de venda sem custo histórico no dia")
    private Long itemsWithoutHistoricalCost;

    @Schema(description = "Turno aberto atual ou null quando não houver turno aberto")
    private CurrentTurnResponse currentTurn;

    @Schema(description = "Até cinco vendas concluídas mais recentes do dia")
    private List<RecentSaleResponse> recentSales;
}
