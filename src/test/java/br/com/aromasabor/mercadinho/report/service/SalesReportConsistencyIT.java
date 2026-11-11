package br.com.aromasabor.mercadinho.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.report.dto.SalesReportResponse;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.entity.SaleItemEntity;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.service.SaleGrossProfitCalculator;
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
                .costPrice(new BigDecimal("2.00"))
                .stock(10)
                .active(true)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build());
        jdbcTemplate.update("""
                INSERT INTO sales (turn_id, status, subtotal, total, created_at)
                VALUES (?, 'COMPLETED', 10.00, 10.00, ?)
                """, turn.getId(), now);
        Long initialSaleId = jdbcTemplate.queryForObject("SELECT max(id) FROM sales", Long.class);
        jdbcTemplate.update("""
                INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal,
                                        unit_cost_price, discount_allocated_amount)
                VALUES (?, ?, 2, 5.00, 10.00, 2.00, 0.00)
                """, initialSaleId, product.getId());

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
            assertThat(report.getGrossProfitKnownAmount()).isEqualByComparingTo("6.00");
            assertThat(report.getGrossProfitComplete()).isTrue();
            assertThat(report.getItemsWithoutHistoricalCost()).isZero();

            reportQueryBarrier.disarm();
            SalesReportResponse subsequent = salesReportService.getReport(today.toString(), today.toString());
            assertThat(subsequent.getTotalRevenue()).isEqualByComparingTo("15.00");
            assertThat(subsequent.getSalesCount()).isEqualTo(2L);
            assertThat(subsequent.getAverageTicket()).isEqualByComparingTo("7.50");
            assertThat(subsequent.getGrossProfitKnownAmount()).isEqualByComparingTo("9.00");
            assertThat(subsequent.getGrossProfitComplete()).isTrue();
            assertThat(subsequent.getItemsWithoutHistoricalCost()).isZero();
            assertThat(subsequent.getDailySales()).extracting(entry -> entry.getSalesCount())
                    .containsExactly(2L);
        } finally {
            continueReport.countDown();
            reportQueryBarrier.disarm();
        }
    }

    @Test
    void shouldReturnNullPeriodProfitWhenKnownAndIndeterminateCompletedSalesShareThePeriod() {
        LocalDate today = LocalDate.now();
        TurnEntity turn = turnRepository.saveAndFlush(TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(today.atTime(8, 0))
                .operatorName("Operador")
                .status(TurnStatus.OPEN)
                .build());
        ProductEntity known = createProduct("789000000030", "6.00");
        Long knownSaleId = saleService.create(new SaleRequestDTO(List.of(
                new SaleItemRequestDTO(known.getId(), 1)))).getId();
        // only one turn may be open; the legacy helper opens its own turn
        jdbcTemplate.update("update turns set status = 'CLOSED', closed_at = ? where id = ?",
                today.atTime(10, 0), turn.getId());
        insertLegacySaleWithMissingCostAndNullAllocations(today.atTime(12, 0), new BigDecimal("3.00"));

        SalesReportResponse report = salesReportService.getReport(today.toString(), today.toString());

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from sales
                where status = 'COMPLETED' and cast(created_at as date) = ?
                """, Integer.class, java.sql.Date.valueOf(today))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select turn_id from sales where id = ?", UUID.class, knownSaleId)).isEqualTo(turn.getId());
        assertThat(report.getSalesCount()).isEqualTo(2L);
        assertThat(report.getTotalRevenue()).isEqualByComparingTo("37.00");
        assertThat(report.getAverageTicket()).isEqualByComparingTo("18.50");
        assertThat(report.getGrossProfitKnownAmount()).isNull();
        assertThat(report.getGrossProfitComplete()).isFalse();
        assertThat(report.getItemsWithoutHistoricalCost()).isEqualTo(1L);
    }

    @Test
    void shouldDeductHeaderDiscountOnceWhenAllCostsAreKnownAndItemAllocationsAreMissing() {
        LocalDate today = LocalDate.now();
        Long saleId = insertCompletedSale(today.atTime(12, 0), "30.00", "3.00");
        ProductEntity first = createProduct("789000000031", "6.00");
        ProductEntity second = createProduct("789000000032", "4.00");
        insertItem(saleId, first, "10.00", "6.00", null);
        insertItem(saleId, second, "20.00", "4.00", null);

        SalesReportResponse report = salesReportService.getReport(today.toString(), today.toString());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sale_items where sale_id = ? and discount_allocated_amount is not null",
                Integer.class, saleId)).isZero();
        assertThat(report.getSalesCount()).isEqualTo(1L);
        assertThat(report.getTotalRevenue()).isEqualByComparingTo("27.00");
        // (10 - 6) + (20 - 4) - 3.00 header discount, deducted once
        assertThat(report.getGrossProfitKnownAmount()).isEqualByComparingTo("17.00");
        assertThat(report.getGrossProfitComplete()).isTrue();
        assertThat(report.getItemsWithoutHistoricalCost()).isZero();
    }

    @Test
    void shouldKeepKnownProfitWhenOnlyTheUnknownCostItemLacksAllocation() {
        LocalDate today = LocalDate.now();
        Long saleId = insertCompletedSale(today.atTime(12, 0), "30.00", "3.00");
        ProductEntity known = createProduct("789000000033", "6.00");
        ProductEntity unknown = createProduct("789000000034", null);
        insertItem(saleId, known, "10.00", "6.00", "1.00");
        insertItem(saleId, unknown, "20.00", null, null);

        SalesReportResponse report = salesReportService.getReport(today.toString(), today.toString());

        assertThat(report.getSalesCount()).isEqualTo(1L);
        assertThat(report.getTotalRevenue()).isEqualByComparingTo("27.00");
        // known item: (10 - 6) - 1.00 allocated; the unknown-cost item contributes nothing
        assertThat(report.getGrossProfitKnownAmount()).isEqualByComparingTo("3.00");
        assertThat(report.getGrossProfitComplete()).isFalse();
        assertThat(report.getItemsWithoutHistoricalCost()).isEqualTo(1L);
    }

    private ProductEntity createProduct(String barcode, String costPrice) {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        return productRepository.saveAndFlush(ProductEntity.builder()
                .barcode(barcode)
                .name("Produto " + barcode)
                .price(new BigDecimal("10.00"))
                .costPrice(costPrice == null ? null : new BigDecimal(costPrice))
                .stock(10)
                .active(true)
                .createdAt(past)
                .updatedAt(past)
                .build());
    }

    private Long insertCompletedSale(LocalDateTime createdAt, String subtotal, String discount) {
        TurnEntity turn = turnRepository.saveAndFlush(TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(createdAt.minusHours(1))
                .operatorName("Operador legado")
                .status(TurnStatus.OPEN)
                .build());
        BigDecimal subtotalAmount = new BigDecimal(subtotal);
        BigDecimal discountAmount = new BigDecimal(discount);
        jdbcTemplate.update("""
                INSERT INTO sales (turn_id, status, subtotal, discount_amount, total, created_at)
                VALUES (?, 'COMPLETED', ?, ?, ?, ?)
                """, turn.getId(), subtotalAmount, discountAmount, subtotalAmount.subtract(discountAmount),
                createdAt);
        return jdbcTemplate.queryForObject("SELECT max(id) FROM sales", Long.class);
    }

    private void insertItem(Long saleId, ProductEntity product, String price, String cost, String allocation) {
        BigDecimal unitPrice = new BigDecimal(price);
        jdbcTemplate.update("""
                INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal,
                                        unit_cost_price, discount_allocated_amount)
                VALUES (?, ?, 1, ?, ?, ?, ?)
                """, saleId, product.getId(), unitPrice, unitPrice,
                cost == null ? null : new BigDecimal(cost),
                allocation == null ? null : new BigDecimal(allocation));
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

        Long completedSaleId = saleService.create(new SaleRequestDTO(List.of(
                new SaleItemRequestDTO(withCost.getId(), 2)))).getId();
        jdbcTemplate.update("update sales set subtotal = 27.50, total = 27.50 where id = ?", completedSaleId);
        jdbcTemplate.update("""
                INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal)
                VALUES (?, ?, 3, 2.50, 7.50)
                """, completedSaleId, withoutCost.getId());

        jdbcTemplate.update("""
                INSERT INTO sales (turn_id, status, subtotal, total, created_at)
                VALUES (?, 'CANCELLED', 1000.00, 1000.00, ?)
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

    @Test
    void shouldReturnIndeterminateProfitForLegacySaleWithMissingCostAndUnallocatedDiscount() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = today.atTime(12, 0);
        insertLegacySaleWithMissingCostAndNullAllocations(now, new BigDecimal("3.00"));

        SalesReportResponse report = salesReportService.getReport(today.toString(), today.toString());

        SaleEntity sale = SaleEntity.builder()
                .status("COMPLETED")
                .discountAmount(new BigDecimal("3.00"))
                .build();
        sale.addItem(SaleItemEntity.builder()
                .unitPrice(new BigDecimal("10.00"))
                .unitCostPrice(new BigDecimal("6.00"))
                .quantity(1)
                .build());
        sale.addItem(SaleItemEntity.builder()
                .unitPrice(new BigDecimal("20.00"))
                .unitCostPrice(null)
                .quantity(1)
                .build());

        assertThat(SaleGrossProfitCalculator.calculate(sale).knownAmount()).isNull();
        assertThat(report.getGrossProfitKnownAmount()).isNull();
        assertThat(report.getGrossProfitComplete()).isFalse();
        assertThat(report.getItemsWithoutHistoricalCost()).isEqualTo(1L);
    }

    @Test
    void shouldCalculateKnownContributionForLegacySaleWithMissingCostAndZeroDiscount() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = today.atTime(12, 0);
        insertLegacySaleWithMissingCostAndNullAllocations(now, BigDecimal.ZERO);

        SalesReportResponse report = salesReportService.getReport(today.toString(), today.toString());

        assertThat(report.getGrossProfitKnownAmount()).isEqualByComparingTo("4.00");
        assertThat(report.getGrossProfitComplete()).isFalse();
        assertThat(report.getItemsWithoutHistoricalCost()).isEqualTo(1L);
    }

    private void insertLegacySaleWithMissingCostAndNullAllocations(
            LocalDateTime createdAt,
            BigDecimal discountAmount) {
        TurnEntity turn = turnRepository.saveAndFlush(TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(createdAt.minusHours(1))
                .operatorName("Operador legado")
                .status(TurnStatus.OPEN)
                .build());
        ProductEntity withCost = productRepository.saveAndFlush(ProductEntity.builder()
                .barcode("789000000020")
                .name("Produto com custo legado")
                .price(new BigDecimal("10.00"))
                .costPrice(new BigDecimal("6.00"))
                .stock(10)
                .active(true)
                .createdAt(createdAt.minusDays(1))
                .updatedAt(createdAt.minusDays(1))
                .build());
        ProductEntity withoutCost = productRepository.saveAndFlush(ProductEntity.builder()
                .barcode("789000000021")
                .name("Produto sem custo legado")
                .price(new BigDecimal("20.00"))
                .costPrice(null)
                .stock(10)
                .active(true)
                .createdAt(createdAt.minusDays(1))
                .updatedAt(createdAt.minusDays(1))
                .build());

        jdbcTemplate.update("""
                INSERT INTO sales (turn_id, status, subtotal, discount_amount, total, created_at)
                VALUES (?, 'COMPLETED', 30.00, ?, ?, ?)
                """, turn.getId(), discountAmount, new BigDecimal("30.00").subtract(discountAmount), createdAt);
        Long saleId = jdbcTemplate.queryForObject("SELECT max(id) FROM sales", Long.class);
        jdbcTemplate.update("""
                INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal, unit_cost_price)
                VALUES (?, ?, 1, 10.00, 10.00, 6.00)
                """, saleId, withCost.getId());
        jdbcTemplate.update("""
                INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal, unit_cost_price)
                VALUES (?, ?, 1, 20.00, 20.00, NULL)
                """, saleId, withoutCost.getId());
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
