package br.com.aromasabor.mercadinho.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.dashboard.dto.DashboardSummaryResponse;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesSummaryProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.RecentSaleProjection;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private TurnRepository turnRepository;

    private DashboardService dashboardService;
    private LocalDate date;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(saleRepository, turnRepository);
        date = LocalDate.now(ZoneId.systemDefault());
        start = date.atStartOfDay();
        end = date.plusDays(1).atStartOfDay();
        when(saleRepository.findRecentSales(start, end, PageRequest.of(0, 5))).thenReturn(List.of());
        when(turnRepository.findFirstByStatusOrderByOpenedAtDesc(TurnStatus.OPEN)).thenReturn(Optional.empty());
    }

    @Test
    void shouldReturnZeroMetricsAndNoRecentSalesWhenThereAreNoSales() {
        when(saleRepository.getDailySummary(start, end)).thenReturn(new DailySummary(0L, BigDecimal.ZERO));

        DashboardSummaryResponse response = dashboardService.getSummary();

        assertThat(response.getDate()).isEqualTo(date);
        assertThat(response.getRevenue()).isEqualByComparingTo("0.00");
        assertThat(response.getSalesCount()).isZero();
        assertThat(response.getAverageTicket()).isEqualByComparingTo("0.00");
        assertThat(response.getCurrentTurn()).isNull();
        assertThat(response.getRecentSales()).isEmpty();
        verify(saleRepository).getDailySummary(start, end);
    }

    @Test
    void shouldReturnPersistedValueAsAverageForOneSale() {
        when(saleRepository.getDailySummary(start, end))
                .thenReturn(new DailySummary(1L, new BigDecimal("12.35")));
        when(saleRepository.findRecentSales(start, end, PageRequest.of(0, 5)))
                .thenReturn(List.of(recentSale(42L, start.plusHours(2), "12.35")));

        DashboardSummaryResponse response = dashboardService.getSummary();

        assertThat(response.getRevenue()).isEqualByComparingTo("12.35");
        assertThat(response.getSalesCount()).isEqualTo(1L);
        assertThat(response.getAverageTicket()).isEqualByComparingTo("12.35");
        assertThat(response.getRecentSales()).singleElement()
                .satisfies(sale -> {
                    assertThat(sale.getId()).isEqualTo(42L);
                    assertThat(sale.getTotal()).isEqualByComparingTo("12.35");
                });
    }

    @Test
    void shouldCalculateAverageTicketFromMultipleSalesWithTwoDecimalPlaces() {
        when(saleRepository.getDailySummary(start, end))
                .thenReturn(new DailySummary(2L, new BigDecimal("52.55")));

        DashboardSummaryResponse response = dashboardService.getSummary();

        assertThat(response.getRevenue()).isEqualByComparingTo("52.55");
        assertThat(response.getSalesCount()).isEqualTo(2L);
        assertThat(response.getAverageTicket()).isEqualByComparingTo("26.28");
    }

    @Test
    void shouldReturnOpenTurnAndRequestRecentSalesWithDatabaseLimitOfFive() {
        UUID turnId = UUID.randomUUID();
        LocalDateTime openedAt = start.plusHours(1);
        when(saleRepository.getDailySummary(start, end))
                .thenReturn(new DailySummary(5L, new BigDecimal("100.00")));
        when(saleRepository.findRecentSales(start, end, PageRequest.of(0, 5)))
                .thenReturn(List.of(
                        recentSale(5L, start.plusHours(5), "50.00"),
                        recentSale(4L, start.plusHours(4), "25.00"),
                        recentSale(3L, start.plusHours(3), "10.00"),
                        recentSale(2L, start.plusHours(2), "10.00"),
                        recentSale(1L, start.plusHours(1), "5.00")));
        when(turnRepository.findFirstByStatusOrderByOpenedAtDesc(TurnStatus.OPEN))
                .thenReturn(Optional.of(TurnEntity.builder()
                        .id(turnId)
                        .openedAt(openedAt)
                        .operatorName("Maria")
                        .status(TurnStatus.OPEN)
                        .build()));

        DashboardSummaryResponse response = dashboardService.getSummary();

        assertThat(response.getCurrentTurn().getId()).isEqualTo(turnId);
        assertThat(response.getCurrentTurn().getOpenedAt()).isEqualTo(openedAt);
        assertThat(response.getRecentSales()).hasSize(5)
                .extracting(sale -> sale.getId())
                .containsExactly(5L, 4L, 3L, 2L, 1L);
        verify(saleRepository).findRecentSales(eq(start), eq(end), eq(PageRequest.of(0, 5)));
    }

    private RecentSaleProjection recentSale(Long id, LocalDateTime createdAt, String total) {
        return new RecentSaleProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return createdAt;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }

    private record DailySummary(Long salesCount, BigDecimal totalRevenue)
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
}
