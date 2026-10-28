package br.com.aromasabor.mercadinho.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.report.dto.SalesReportResponse;
import br.com.aromasabor.mercadinho.report.exception.InvalidSalesReportPeriodException;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesReportProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesSummaryProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.GrossProfitSummaryProjection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesReportServiceTest {

    @Mock
    private SaleRepository saleRepository;

    private SalesReportService salesReportService;

    @BeforeEach
    void setUp() {
        salesReportService = new SalesReportService(saleRepository);
    }

    @Test
    void shouldReturnPeriodTotalsAndCompleteDailySeriesWithZeroDays() {
        LocalDateTime start = LocalDate.of(2026, 10, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 10, 5).atStartOfDay();
        when(saleRepository.getDailySummary(start, end)).thenReturn(new SalesSummary(3L, new BigDecimal("61.00")));
        when(saleRepository.getGrossProfitSummary(start, end))
                .thenReturn(new ProfitSummary(new BigDecimal("24.00"), 1L));
        when(saleRepository.getDailySalesReport(start, end)).thenReturn(List.of(
                dailySummary(LocalDate.of(2026, 10, 1), 2L, "40.00"),
                dailySummary(LocalDate.of(2026, 10, 3), 1L, "21.00")));

        SalesReportResponse response = salesReportService.getReport("2026-10-01", "2026-10-04");

        assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(response.getTotalRevenue()).isEqualByComparingTo("61.00");
        assertThat(response.getSalesCount()).isEqualTo(3L);
        assertThat(response.getAverageTicket()).isEqualByComparingTo("20.33");
        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("24.00");
        assertThat(response.getGrossProfitComplete()).isFalse();
        assertThat(response.getItemsWithoutHistoricalCost()).isEqualTo(1L);
        assertThat(response.getDailySales()).hasSize(4);
        assertThat(response.getDailySales()).extracting(entry -> entry.getDate())
                .containsExactly(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 2),
                        LocalDate.of(2026, 10, 3),
                        LocalDate.of(2026, 10, 4));
        assertThat(response.getDailySales()).extracting(entry -> entry.getRevenue())
                .containsExactly(
                        new BigDecimal("40.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("21.00"),
                        new BigDecimal("0.00"));
        assertThat(response.getDailySales()).extracting(entry -> entry.getSalesCount())
                .containsExactly(2L, 0L, 1L, 0L);
        verify(saleRepository).getDailySummary(start, end);
        verify(saleRepository).getDailySalesReport(start, end);
    }

    @Test
    void shouldReturnZeroTotalsAndCompleteZeroSeriesWhenPeriodHasNoSales() {
        LocalDateTime start = LocalDate.of(2026, 10, 8).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 10, 10).atStartOfDay();
        when(saleRepository.getDailySummary(start, end)).thenReturn(new SalesSummary(0L, BigDecimal.ZERO));
        when(saleRepository.getGrossProfitSummary(start, end))
                .thenReturn(new ProfitSummary(BigDecimal.ZERO, 0L));
        when(saleRepository.getDailySalesReport(start, end)).thenReturn(List.of());

        SalesReportResponse response = salesReportService.getReport("2026-10-08", "2026-10-09");

        assertThat(response.getTotalRevenue()).isEqualByComparingTo("0.00");
        assertThat(response.getSalesCount()).isZero();
        assertThat(response.getAverageTicket()).isEqualByComparingTo("0.00");
        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("0.00");
        assertThat(response.getGrossProfitComplete()).isTrue();
        assertThat(response.getItemsWithoutHistoricalCost()).isZero();
        assertThat(response.getDailySales()).hasSize(2)
                .allSatisfy(entry -> {
                    assertThat(entry.getRevenue()).isEqualByComparingTo("0.00");
                    assertThat(entry.getSalesCount()).isZero();
                });
    }

    @Test
    void shouldAcceptSingleDayAndReturnItsAggregates() {
        LocalDateTime start = LocalDate.of(2026, 10, 9).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 10, 10).atStartOfDay();
        when(saleRepository.getDailySummary(start, end)).thenReturn(new SalesSummary(1L, new BigDecimal("7.25")));
        when(saleRepository.getGrossProfitSummary(start, end))
                .thenReturn(new ProfitSummary(new BigDecimal("2.25"), 0L));
        when(saleRepository.getDailySalesReport(start, end))
                .thenReturn(List.of(dailySummary(LocalDate.of(2026, 10, 9), 1L, "7.25")));

        SalesReportResponse response = salesReportService.getReport("2026-10-09", "2026-10-09");

        assertThat(response.getDailySales()).hasSize(1);
        assertThat(response.getDailySales().get(0).getDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(response.getAverageTicket()).isEqualByComparingTo("7.25");
        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("2.25");
    }

    @Test
    void shouldRejectMissingOrMalformedDateBeforeQueryingRepository() {
        assertThatThrownBy(() -> salesReportService.getReport(null, "2026-10-01"))
                .isInstanceOf(InvalidSalesReportPeriodException.class)
                .hasMessageContaining("startDate");
        assertThatThrownBy(() -> salesReportService.getReport("2026-10-01", null))
                .isInstanceOf(InvalidSalesReportPeriodException.class)
                .hasMessageContaining("endDate");
        assertThatThrownBy(() -> salesReportService.getReport("01-10-2026", "2026-10-01"))
                .isInstanceOf(InvalidSalesReportPeriodException.class)
                .hasMessageContaining("yyyy-MM-dd");
        assertThatThrownBy(() -> salesReportService.getReport("2026-02-30", "2026-03-01"))
                .isInstanceOf(InvalidSalesReportPeriodException.class)
                .hasMessageContaining("data válida");

        verifyNoInteractions(saleRepository);
    }

    @Test
    void shouldRejectInvertedPeriodBeforeQueryingRepository() {
        assertThatThrownBy(() -> salesReportService.getReport("2026-10-02", "2026-10-01"))
                .isInstanceOf(InvalidSalesReportPeriodException.class)
                .hasMessage("startDate não pode ser posterior a endDate.");

        verifyNoInteractions(saleRepository);
    }

    private DailySalesReportProjection dailySummary(LocalDate date, Long count, String revenue) {
        return new DailySalesReportProjection() {
            @Override
            public LocalDate getSaleDate() {
                return date;
            }

            @Override
            public Long getSalesCount() {
                return count;
            }

            @Override
            public BigDecimal getRevenue() {
                return new BigDecimal(revenue);
            }
        };
    }

    private record SalesSummary(Long salesCount, BigDecimal totalRevenue)
            implements DailySalesSummaryProjection {

        @Override
        public Long getSalesCount() {
            return salesCount;
        }

        @Override
        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }
    }

    private record ProfitSummary(BigDecimal grossProfitKnownAmount, Long itemsWithoutHistoricalCost)
            implements GrossProfitSummaryProjection {
        @Override
        public BigDecimal getGrossProfitKnownAmount() {
            return grossProfitKnownAmount;
        }

        @Override
        public Long getItemsWithoutHistoricalCost() {
            return itemsWithoutHistoricalCost;
        }
    }
}
