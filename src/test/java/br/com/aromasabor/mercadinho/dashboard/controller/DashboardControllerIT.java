package br.com.aromasabor.mercadinho.dashboard.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import br.com.aromasabor.mercadinho.dashboard.dto.DashboardSummaryResponse;
import br.com.aromasabor.mercadinho.dashboard.dto.LowStockProductResponse;
import br.com.aromasabor.mercadinho.dashboard.dto.TopProductResponse;
import br.com.aromasabor.mercadinho.dashboard.service.DashboardService;
import br.com.aromasabor.mercadinho.dashboard.service.DashboardInsightsService;
import br.com.aromasabor.mercadinho.product.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardControllerIT {

    private MockMvc mockMvc;
    private DashboardService dashboardService;
    private DashboardInsightsService dashboardInsightsService;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        dashboardInsightsService = mock(DashboardInsightsService.class);
        DashboardController controller = new DashboardController(dashboardService, dashboardInsightsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnDashboardSummaryWithExplicitAbsenceOfOpenTurn() throws Exception {
        when(dashboardService.getSummary()).thenReturn(new DashboardSummaryResponse(
                LocalDate.of(2026, 10, 26),
                new BigDecimal("45.50"),
                2L,
                new BigDecimal("22.75"),
                new BigDecimal("12.00"),
                true,
                0L,
                null,
                List.of()));

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-26"))
                .andExpect(jsonPath("$.revenue").value(45.50))
                .andExpect(jsonPath("$.salesCount").value(2))
                .andExpect(jsonPath("$.averageTicket").value(22.75))
                .andExpect(jsonPath("$.grossProfitKnownAmount").value(12.00))
                .andExpect(jsonPath("$.grossProfitComplete").value(true))
                .andExpect(jsonPath("$.itemsWithoutHistoricalCost").value(0))
                .andExpect(jsonPath("$.currentTurn").hasJsonPath())
                .andExpect(jsonPath("$.currentTurn").value(nullValue()))
                .andExpect(jsonPath("$.recentSales").isArray())
                .andExpect(jsonPath("$.recentSales").isEmpty());
    }

    @Test
    void shouldReturnTopProductsWithQuantitiesSold() throws Exception {
        when(dashboardInsightsService.getTopProducts())
                .thenReturn(List.of(new TopProductResponse(23L, "Café", 14L)));

        mockMvc.perform(get("/api/dashboard/top-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(23))
                .andExpect(jsonPath("$[0].productName").value("Café"))
                .andExpect(jsonPath("$[0].quantitySold").value(14));
    }

    @Test
    void shouldReturnLowStockProductsWithIdentificationAndStockFields() throws Exception {
        when(dashboardInsightsService.getLowStockProducts())
                .thenReturn(List.of(new LowStockProductResponse(8L, "Arroz", "789123", 0)));

        mockMvc.perform(get("/api/dashboard/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8))
                .andExpect(jsonPath("$[0].name").value("Arroz"))
                .andExpect(jsonPath("$[0].barcode").value("789123"))
                .andExpect(jsonPath("$[0].stock").value(0));
    }
}
