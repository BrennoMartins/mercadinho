package br.com.aromasabor.mercadinho.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aromasabor.mercadinho.dashboard.dto.DashboardSummaryResponse;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.service.SaleService;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(DashboardConsistencyIT.DashboardQueryBarrierConfiguration.class)
class DashboardConsistencyIT {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private SaleService saleService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TurnRepository turnRepository;

    @Autowired
    private DashboardQueryBarrier barrier;

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
                "TRUNCATE TABLE stock_movements, sale_items, sales, turns, products RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldUseOneSnapshotWhenSaleCommitsBetweenDashboardQueries() throws Exception {
        LocalDateTime now = LocalDateTime.now();
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
                .costPrice(new BigDecimal("2.00"))
                .stock(10)
                .active(true)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build());
        Long initialSaleId = saleService.create(
                new SaleRequestDTO(List.of(new SaleItemRequestDTO(product.getId(), 2)))).getId();
        jdbcTemplate.update("""
                INSERT INTO sales (turn_id, status, subtotal, total, created_at)
                VALUES (?, 'CANCELLED', 1000.00, 1000.00, ?)
                """, turn.getId(), now);

        CountDownLatch summaryQueryCompleted = new CountDownLatch(1);
        CountDownLatch continueDashboard = new CountDownLatch(1);
        barrier.arm(summaryQueryCompleted, continueDashboard);

        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                var dashboardFuture = executor.submit(dashboardService::getSummary);
                if (!summaryQueryCompleted.await(10, TimeUnit.SECONDS)) {
                    dashboardFuture.get(1, TimeUnit.SECONDS);
                    throw new AssertionError("The summary query did not reach the concurrency barrier.");
                }

                Long newSaleId = saleService.create(
                        new SaleRequestDTO(List.of(new SaleItemRequestDTO(product.getId(), 1)))).getId();
                continueDashboard.countDown();

                DashboardSummaryResponse snapshot = dashboardFuture.get(10, TimeUnit.SECONDS);
                assertThat(snapshot.getDate()).isEqualTo(LocalDate.now());
                assertThat(snapshot.getRevenue()).isEqualByComparingTo("10.00");
                assertThat(snapshot.getSalesCount()).isEqualTo(1L);
                assertThat(snapshot.getAverageTicket()).isEqualByComparingTo("10.00");
                assertThat(snapshot.getGrossProfitKnownAmount()).isEqualByComparingTo("6.00");
                assertThat(snapshot.getGrossProfitComplete()).isTrue();
                assertThat(snapshot.getItemsWithoutHistoricalCost()).isZero();
                assertThat(snapshot.getCurrentTurn().getId()).isEqualTo(turn.getId());
                assertThat(snapshot.getRecentSales()).singleElement().satisfies(sale -> {
                    assertThat(sale.getId()).isEqualTo(initialSaleId);
                    assertThat(sale.getTotal()).isEqualByComparingTo("10.00");
                });

                barrier.disarm();
                DashboardSummaryResponse subsequent = dashboardService.getSummary();
                assertThat(subsequent.getRevenue()).isEqualByComparingTo("15.00");
                assertThat(subsequent.getSalesCount()).isEqualTo(2L);
                assertThat(subsequent.getAverageTicket()).isEqualByComparingTo("7.50");
                assertThat(subsequent.getGrossProfitKnownAmount()).isEqualByComparingTo("9.00");
                assertThat(subsequent.getGrossProfitComplete()).isTrue();
                assertThat(subsequent.getItemsWithoutHistoricalCost()).isZero();
                assertThat(subsequent.getCurrentTurn().getId()).isEqualTo(turn.getId());
                assertThat(subsequent.getRecentSales()).extracting(sale -> sale.getId())
                        .containsExactlyInAnyOrder(initialSaleId, newSaleId);
            } finally {
                continueDashboard.countDown();
                barrier.disarm();
            }
        }
    }

    static class DashboardQueryBarrier {

        private volatile CountDownLatch summaryQueryCompleted;
        private volatile CountDownLatch continueDashboard;

        void arm(CountDownLatch summaryQueryCompleted, CountDownLatch continueDashboard) {
            this.summaryQueryCompleted = summaryQueryCompleted;
            this.continueDashboard = continueDashboard;
        }

        void afterSummaryQuery() throws InterruptedException {
            CountDownLatch completed = summaryQueryCompleted;
            CountDownLatch continueSignal = continueDashboard;
            if (completed != null && continueSignal != null) {
                completed.countDown();
                if (!continueSignal.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to continue the dashboard query.");
                }
            }
        }

        void disarm() {
            summaryQueryCompleted = null;
            continueDashboard = null;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DashboardQueryBarrierConfiguration {

        @Bean
        DashboardQueryBarrier dashboardQueryBarrier() {
            return new DashboardQueryBarrier();
        }

        @Bean
        @Primary
        SaleRepository barrierSaleRepository(
                @Qualifier("saleRepository") SaleRepository delegate,
                DashboardQueryBarrier barrier) {
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
