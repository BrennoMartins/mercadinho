package br.com.aromasabor.mercadinho.report.controller;

import br.com.aromasabor.mercadinho.report.dto.SalesReportResponse;
import br.com.aromasabor.mercadinho.report.service.SalesReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/sales")
@RequiredArgsConstructor
@Tag(name = "Sales reports")
public class SalesReportController {

    private final SalesReportService salesReportService;

    @GetMapping
    @Operation(
            summary = "Consultar relatório de vendas por período",
            description = "As datas inicial e final são inclusivas e representam dias completos. "
                    + "São consideradas somente vendas COMPLETED.")
    @ApiResponse(responseCode = "200", description = "Relatório de vendas retornado com sucesso")
    @ApiResponse(responseCode = "400", description = "Datas ausentes, inválidas ou período invertido")
    public SalesReportResponse getReport(
            @Parameter(description = "Primeiro dia incluído, no formato ISO yyyy-MM-dd",
                    required = true, example = "2026-10-01",
                    schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String startDate,
            @Parameter(description = "Último dia incluído, no formato ISO yyyy-MM-dd",
                    required = true, example = "2026-10-07",
                    schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String endDate) {
        return salesReportService.getReport(startDate, endDate);
    }
}
