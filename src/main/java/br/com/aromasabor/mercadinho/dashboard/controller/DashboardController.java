package br.com.aromasabor.mercadinho.dashboard.controller;

import br.com.aromasabor.mercadinho.dashboard.dto.DashboardSummaryResponse;
import br.com.aromasabor.mercadinho.dashboard.dto.LowStockProductResponse;
import br.com.aromasabor.mercadinho.dashboard.dto.TopProductResponse;
import br.com.aromasabor.mercadinho.dashboard.service.DashboardService;
import br.com.aromasabor.mercadinho.dashboard.service.DashboardInsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardInsightsService dashboardInsightsService;

    @GetMapping("/summary")
    @Operation(summary = "Obter indicadores operacionais do dia atual",
            description = "Inclui lucro bruto conhecido e a quantidade de itens sem custo histórico.")
    @ApiResponse(responseCode = "200", description = "Resumo operacional retornado com sucesso")
    public DashboardSummaryResponse getSummary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/top-products")
    @Operation(
            summary = "Listar produtos mais vendidos nos últimos sete dias",
            description = "Considera os sete dias-calendário incluindo hoje, do início do sexto dia anterior "
                    + "ao início de amanhã, no fuso padrão da JVM. Inclui somente vendas COMPLETED.")
    @ApiResponse(responseCode = "200", description = "Ranking de produtos retornado com sucesso")
    public List<TopProductResponse> getTopProducts() {
        return dashboardInsightsService.getTopProducts();
    }

    @GetMapping("/low-stock")
    @Operation(
            summary = "Listar produtos ativos com estoque baixo",
            description = "Retorna todos os produtos ativos com estoque menor ou igual a cinco unidades.")
    @ApiResponse(responseCode = "200", description = "Produtos com estoque baixo retornados com sucesso")
    public List<LowStockProductResponse> getLowStockProducts() {
        return dashboardInsightsService.getLowStockProducts();
    }
}
