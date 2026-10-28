package br.com.aromasabor.mercadinho.report.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.exception.GlobalExceptionHandler;
import br.com.aromasabor.mercadinho.report.dto.DailySalesReportEntry;
import br.com.aromasabor.mercadinho.report.dto.SalesReportResponse;
import br.com.aromasabor.mercadinho.report.exception.InvalidSalesReportPeriodException;
import br.com.aromasabor.mercadinho.report.service.SalesReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SalesReportControllerIT {

    private MockMvc mockMvc;
    private SalesReportService salesReportService;

    @BeforeEach
    void setUp() {
        salesReportService = mock(SalesReportService.class);
        SalesReportController controller = new SalesReportController(salesReportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnReportForValidInclusivePeriod() throws Exception {
        when(salesReportService.getReport("2026-10-01", "2026-10-03"))
                .thenReturn(new SalesReportResponse(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 3),
                        new BigDecimal("61.00"),
                        3L,
                        new BigDecimal("20.33"),
                        new BigDecimal("18.00"),
                        false,
                        1L,
                        List.of(
                                new DailySalesReportEntry(LocalDate.of(2026, 10, 1), new BigDecimal("40.00"), 2L),
                                new DailySalesReportEntry(LocalDate.of(2026, 10, 2), new BigDecimal("0.00"), 0L),
                                new DailySalesReportEntry(LocalDate.of(2026, 10, 3), new BigDecimal("21.00"), 1L))));

        mockMvc.perform(get("/api/reports/sales")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.endDate").value("2026-10-03"))
                .andExpect(jsonPath("$.totalRevenue").value(61.00))
                .andExpect(jsonPath("$.salesCount").value(3))
                .andExpect(jsonPath("$.averageTicket").value(20.33))
                .andExpect(jsonPath("$.grossProfitKnownAmount").value(18.00))
                .andExpect(jsonPath("$.grossProfitComplete").value(false))
                .andExpect(jsonPath("$.itemsWithoutHistoricalCost").value(1))
                .andExpect(jsonPath("$.dailySales").isArray())
                .andExpect(jsonPath("$.dailySales").value(org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.dailySales[1].date").value("2026-10-02"))
                .andExpect(jsonPath("$.dailySales[1].revenue").value(0.00))
                .andExpect(jsonPath("$.dailySales[1].salesCount").value(0));

        verify(salesReportService).getReport("2026-10-01", "2026-10-03");
    }

    @Test
    void shouldReturnBadRequestWhenEitherDateParameterIsMissing() throws Exception {
        when(salesReportService.getReport(null, "2026-10-03"))
                .thenThrow(new InvalidSalesReportPeriodException(
                        "startDate é obrigatório e deve estar no formato yyyy-MM-dd."));

        mockMvc.perform(get("/api/reports/sales").param("endDate", "2026-10-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "startDate é obrigatório e deve estar no formato yyyy-MM-dd."));

        when(salesReportService.getReport("2026-10-01", null))
                .thenThrow(new InvalidSalesReportPeriodException(
                        "endDate é obrigatório e deve estar no formato yyyy-MM-dd."));

        mockMvc.perform(get("/api/reports/sales").param("startDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "endDate é obrigatório e deve estar no formato yyyy-MM-dd."));
    }

    @Test
    void shouldReturnBadRequestForMalformedAndInvalidDatesOrInvertedPeriod() throws Exception {
        when(salesReportService.getReport("01-10-2026", "2026-10-03"))
                .thenThrow(new InvalidSalesReportPeriodException(
                        "startDate é obrigatório e deve estar no formato yyyy-MM-dd."));
        mockMvc.perform(get("/api/reports/sales")
                        .param("startDate", "01-10-2026")
                        .param("endDate", "2026-10-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        when(salesReportService.getReport("2026-02-30", "2026-03-01"))
                .thenThrow(new InvalidSalesReportPeriodException("startDate não é uma data válida."));
        mockMvc.perform(get("/api/reports/sales")
                        .param("startDate", "2026-02-30")
                        .param("endDate", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate não é uma data válida."));

        when(salesReportService.getReport("2026-10-04", "2026-10-03"))
                .thenThrow(new InvalidSalesReportPeriodException(
                        "startDate não pode ser posterior a endDate."));
        mockMvc.perform(get("/api/reports/sales")
                        .param("startDate", "2026-10-04")
                        .param("endDate", "2026-10-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate não pode ser posterior a endDate."));
    }
}
