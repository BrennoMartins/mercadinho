package br.com.aromasabor.mercadinho.report.dto;

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
@Schema(description = "Relatório de vendas concluídas para um período inclusivo")
public class SalesReportResponse {

    @Schema(description = "Primeiro dia incluído no relatório", example = "2026-10-01")
    private LocalDate startDate;

    @Schema(description = "Último dia incluído no relatório", example = "2026-10-07")
    private LocalDate endDate;

    @Schema(description = "Faturamento total persistido no período", example = "1234.56")
    private BigDecimal totalRevenue;

    @Schema(description = "Quantidade de vendas concluídas no período", example = "42")
    private Long salesCount;

    @Schema(description = "Faturamento total dividido pela quantidade de vendas, arredondado a duas casas",
            example = "29.39")
    private BigDecimal averageTicket;

    @Schema(description = "Série diária completa em ordem cronológica, incluindo dias sem vendas")
    private List<DailySalesReportEntry> dailySales;
}
