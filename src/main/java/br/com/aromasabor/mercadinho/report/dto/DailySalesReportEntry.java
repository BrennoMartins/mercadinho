package br.com.aromasabor.mercadinho.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Indicadores de vendas concluídas para um dia do período")
public class DailySalesReportEntry {

    @Schema(description = "Data da agregação diária", example = "2026-10-01")
    private LocalDate date;

    @Schema(description = "Faturamento persistido das vendas concluídas no dia", example = "125.50")
    private BigDecimal revenue;

    @Schema(description = "Quantidade de vendas concluídas no dia", example = "5")
    private Long salesCount;
}
