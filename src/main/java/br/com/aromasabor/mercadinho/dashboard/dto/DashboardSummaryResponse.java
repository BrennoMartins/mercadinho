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

    @Schema(description = "Turno aberto atual ou null quando não houver turno aberto")
    private CurrentTurnResponse currentTurn;

    @Schema(description = "Até cinco vendas concluídas mais recentes do dia")
    private List<RecentSaleResponse> recentSales;
}
