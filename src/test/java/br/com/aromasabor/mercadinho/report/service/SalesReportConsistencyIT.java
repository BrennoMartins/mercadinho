package br.com.aromasabor.mercadinho.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.report.dto.SalesReportResponse;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.service.SaleService;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(SalesReportConsistencyIT.ReportQueryBarrierConfiguration.class)
class SalesReportConsistencyIT {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SalesReportService salesReportService;

    @Autowired
    private SaleService saleService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TurnRepository turnRepository;

    @Autowired
    private ReportQueryBarrier reportQueryBarrier;

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
        jdbcTemplate.execute(
                "TRUNCATE TABLE stock_movements, sale_items, sales, turns, products "
                        + "RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldUseOneDatabaseSnapshotWhenSaleCommitsBetweenReportQueries() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDateTime now = today.atTime(12, 0);
        TurnEntity turn = turnRepository.saveAndFlush(TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(now.minusHours(1))
                .operatorName("Operador")
                .status(TurnStatus.OPEN)
                .build());
        ProductEntity product = productRepository.saveAndFlush(ProductEntity.builder()
                .barcode("789000000001")
                .name("Produto")
                .price(new BigDecimal("5.00"))
                .stock(10)
                .active(true)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build());
        jdbcTemplate.update("""
                INSERT INTO sales (turn_id, status, total, created_at)
                VALUES (?, 'COMPLETED', 10.00, ?)
                """, turn.getId(), now);

        CountDownLatch summaryQueryCompleted = new CountDownLatch(1);
        CountDownLatch continueReport = new CountDownLatch(1);
        reportQueryBarrier.arm(summaryQueryCompleted, continueReport);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var reportFuture = executor.submit(() ->
                    salesReportService.getReport(today.toString(), today.toString()));

            if (!summaryQueryCompleted.await(10, TimeUnit.SECONDS)) {
                reportFuture.get(1, TimeUnit.SECONDS);
                throw new AssertionError("The summary query did not reach the concurrency barrier.");
            }

            saleService.create(new SaleRequestDTO(List.of(new SaleItemRequestDTO(product.getId(), 1))));
            continueReport.countDown();

            SalesReportResponse report = reportFuture.get(10, TimeUnit.SECONDS);
            assertThat(report.getTotalRevenue()).isEqualByComparingTo("10.00");
            assertThat(report.getSalesCount()).isEqualTo(1L);
            assertThat(report.getAverageTicket()).isEqualByComparingTo("10.00");
            assertThat(report.getDailySales()).hasSize(1);
            assertThat(report.getDailySales()).extracting(entry -> entry.getRevenue())
                    .containsExactly(new BigDecimal("10.00"));
            assertThat(report.getDailySales()).extracting(entry -> entry.getSalesCount())
                    .containsExactly(1L);
        } finally {
            continueReport.countDown();
            reportQueryBarrier.disarm();
        }
    }

    @Test
    void shouldAggregateHistoricalGrossProfitForTheFullPeriodAndMarkMissingCosts() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = today.atTime(12, 0);
        TurnEntity turn = turnRepository.saveAndFlush(TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(now.minusHours(1))
                .operatorName("Operador")
                .status(TurnStatus.OPEN)
                .build());
        ProductEntity withCost = productRepository.saveAndFlush(ProductEntity.builder()
                .barcode("789000000010")
                .name("Produto com custo")
                .price(new BigDecimal("10.00"))
                .costPrice(new BigDecimal("6.25"))
                .stock(10)
                .active(true)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build());
        ProductEntity withoutCost = productRepository.saveAndFlush(ProductEntity.builder()
                .barcode("789000000011")
                .name("Produto sem custo")
                .price(new BigDecimal("2.50"))
                .costPrice(null)
                .stock(10)
                .active(true)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build());

        saleService.create(new SaleRequestDTO(List.of(
                new SaleItemRequestDTO(withCost.getId(), 2),
                new SaleItemRequestDTO(withoutCost.getId(), 3))));

        jdbcTemplate.update("""
                INSERT INTO sales (turn_id, status, total, created_at)
                VALUES (?, 'CANCELLED', 1000.00, ?)
                """, turn.getId(), now);
        Long cancelledSaleId = jdbcTemplate.queryForObject("SELECT max(id) FROM sales", Long.class);
        jdbcTemplate.update("""
                INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal, unit_cost_price)
                VALUES (?, ?, 100, 10.00, 1000.00, 1.00)
                """, cancelledSaleId, withCost.getId());

        withCost.setCostPrice(new BigDecimal("1.00"));
        withoutCost.setCostPrice(new BigDecimal("1.00"));
        productRepository.saveAllAndFlush(List.of(withCost, withoutCost));

        SalesReportResponse report = salesReportService.getReport(today.toString(), today.toString());

        assertThat(report.getSalesCount()).isEqualTo(1L);
        assertThat(report.getTotalRevenue()).isEqualByComparingTo("27.50");
        assertThat(report.getGrossProfitKnownAmount()).isEqualByComparingTo("7.50");
        assertThat(report.getGrossProfitComplete()).isFalse();
        assertThat(report.getItemsWithoutHistoricalCost()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select si.unit_cost_price from sale_items si join sales s on s.id = si.sale_id "
                        + "where si.product_id = ? and s.status = 'COMPLETED'",
                BigDecimal.class,
                withCost.getId())).isEqualByComparingTo("6.25");
        assertThat(jdbcTemplate.queryForObject(
                "select unit_cost_price from sale_items where product_id = ?",
                BigDecimal.class,
                withoutCost.getId())).isNull();
    }

    static class ReportQueryBarrier {

        private volatile CountDownLatch summaryQueryCompleted;
        private volatile CountDownLatch continueReport;

        void arm(CountDownLatch summaryQueryCompleted, CountDownLatch continueReport) {
            this.summaryQueryCompleted = summaryQueryCompleted;
            this.continueReport = continueReport;
        }

        void afterSummaryQuery() throws InterruptedException {
            CountDownLatch summaryCompleted = summaryQueryCompleted;
            CountDownLatch continueSignal = continueReport;
            if (summaryCompleted != null && continueSignal != null) {
                summaryCompleted.countDown();
                if (!continueSignal.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to continue the report query.");
                }
            }
        }

        void disarm() {
            summaryQueryCompleted = null;
            continueReport = null;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReportQueryBarrierConfiguration {

        @Bean
        ReportQueryBarrier reportQueryBarrier() {
            return new ReportQueryBarrier();
        }

        @Bean
        @Primary
        SaleRepository barrierSaleRepository(
                @Qualifier("saleRepository") SaleRepository delegate,
                ReportQueryBarrier barrier) {
            return (SaleRepository) Proxy.newProxyInstance(
                    SaleRepository.class.getClassLoader(),
                    new Class<?>[]{SaleRepository.class},
                    (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if (method.getName().equals("getDailySummary")) {
                                barrier.afterSummaryQuery();
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start embedded PostgreSQL for integration tests", ex);
        }
    }
}
