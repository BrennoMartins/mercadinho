package br.com.aromasabor.mercadinho.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesSummaryProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesReportProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.RecentSaleProjection;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class DashboardSaleRepositoryIT {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TurnRepository turnRepository;

    @Autowired
    private SaleRepository saleRepository;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        POSTGRES.close();
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE stock_movements, sale_items, sales, turns RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldAggregateCompletedSalesInHalfOpenDayInterval() {
        TurnEntity turn = createTurn();
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = start.plusDays(1);
        createSale(turn, "COMPLETED", start.minusNanos(1_000), "100.00");
        createSale(turn, "COMPLETED", start, "10.00");
        createSale(turn, "COMPLETED", end.minusNanos(1_000), "20.00");
        createSale(turn, "COMPLETED", end, "200.00");
        createSale(turn, "PENDING", start.plusHours(12), "300.00");

        DailySalesSummaryProjection summary = saleRepository.getDailySummary(start, end);

        assertThat(summary.getSalesCount()).isEqualTo(2L);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldReturnAtMostFiveRecentCompletedSalesInDescendingOrderWithStoredTotals() {
        TurnEntity turn = createTurn();
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 0, 0);
        LocalDateTime end = start.plusDays(1);
        for (int index = 1; index <= 7; index++) {
            createSale(turn, "COMPLETED", start.plusHours(index), new BigDecimal("7.00")
                    .multiply(BigDecimal.valueOf(index)).toPlainString());
        }
        createSale(turn, "CANCELLED", start.plusHours(8), "999.00");

        List<RecentSaleProjection> recentSales = saleRepository.findRecentSales(
                start, end, PageRequest.of(0, 5));

        assertThat(recentSales).hasSize(5);
        assertThat(recentSales).extracting(RecentSaleProjection::getTotal)
                .containsExactly(
                        new BigDecimal("49.00"),
                        new BigDecimal("42.00"),
                        new BigDecimal("35.00"),
                        new BigDecimal("28.00"),
                        new BigDecimal("21.00"));
        assertThat(recentSales).extracting(RecentSaleProjection::getCreatedAt)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));
    }

    @Test
    void shouldAggregateCompletedSalesPerDayAcrossMonthAndYearBoundaries() {
        TurnEntity turn = createTurn();
        LocalDateTime start = LocalDateTime.of(2025, 12, 31, 0, 0);
        LocalDateTime endExclusive = LocalDateTime.of(2026, 1, 4, 0, 0);
        createSale(turn, "COMPLETED", start.minusNanos(1_000), "100.00");
        createSale(turn, "COMPLETED", start, "10.25");
        createSale(turn, "COMPLETED", start.plusHours(2), "4.75");
        createSale(turn, "PENDING", start.plusHours(3), "200.00");
        createSale(turn, "COMPLETED", LocalDateTime.of(2026, 1, 2, 12, 0), "8.30");
        createSale(turn, "COMPLETED", LocalDateTime.of(2026, 1, 3, 23, 59, 59, 999_999_000), "12.99");
        createSale(turn, "COMPLETED", endExclusive, "500.00");

        DailySalesSummaryProjection summary = saleRepository.getDailySummary(start, endExclusive);
        List<DailySalesReportProjection> dailySummary = saleRepository.getDailySalesReport(start, endExclusive);

        assertThat(summary.getSalesCount()).isEqualTo(4L);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo("36.29");
        assertThat(dailySummary).extracting(DailySalesReportProjection::getSaleDate)
                .containsExactly(
                        LocalDate.of(2025, 12, 31),
                        LocalDate.of(2026, 1, 2),
                        LocalDate.of(2026, 1, 3));
        assertThat(dailySummary).extracting(DailySalesReportProjection::getSalesCount)
                .containsExactly(2L, 1L, 1L);
        assertThat(dailySummary).extracting(DailySalesReportProjection::getRevenue)
                .containsExactly(new BigDecimal("15.00"), new BigDecimal("8.30"), new BigDecimal("12.99"));
    }

    private TurnEntity createTurn() {
        return turnRepository.saveAndFlush(TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(LocalDateTime.of(2026, 10, 26, 0, 0))
                .operatorName("Operador")
                .status(TurnStatus.OPEN)
                .build());
    }

    private void createSale(TurnEntity turn, String status, LocalDateTime createdAt, String total) {
        saleRepository.saveAndFlush(SaleEntity.builder()
                .turn(turn)
                .status(status)
                .createdAt(createdAt)
                .total(new BigDecimal(total))
                .build());
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start embedded PostgreSQL for integration tests", ex);
        }
    }
}
