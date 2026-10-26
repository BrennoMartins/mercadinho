package br.com.aromasabor.mercadinho.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.product.repository.projection.LowStockProductProjection;
import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.entity.SaleItemEntity;
import br.com.aromasabor.mercadinho.sale.repository.SaleItemRepository;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.DashboardTopProductProjection;
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
class DashboardInsightsRepositoryIT {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TurnRepository turnRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

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
        jdbcTemplate.execute("TRUNCATE TABLE stock_movements, sale_items, sales, turns, products "
                + "RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldAggregateCompletedSalesAcrossSevenCalendarDaysAndKeepHistoricalInactiveProducts() {
        TurnEntity turn = createTurn();
        ProductEntity activeProduct = createProduct("Café", 10, true);
        ProductEntity inactiveProduct = createProduct("Produto desativado", 0, false);
        LocalDateTime start = LocalDate.of(2026, 10, 20).atStartOfDay();
        LocalDateTime end = start.plusDays(7);

        createSale(turn, "COMPLETED", start.minusNanos(1_000),
                item(activeProduct, 100), item(inactiveProduct, 100));
        createSale(turn, "COMPLETED", start,
                item(activeProduct, 2), item(activeProduct, 3), item(inactiveProduct, 1));
        createSale(turn, "COMPLETED", start.plusDays(2), item(activeProduct, 4));
        createSale(turn, "PENDING", start.plusDays(3), item(activeProduct, 50));
        createSale(turn, "CANCELLED", start.plusDays(4), item(activeProduct, 60));
        createSale(turn, "COMPLETED", end.minusNanos(1_000), item(inactiveProduct, 2));
        createSale(turn, "COMPLETED", end, item(activeProduct, 200));

        List<DashboardTopProductProjection> topProducts = saleItemRepository.findTopProductsBetween(
                start, end, PageRequest.of(0, 5));

        assertThat(topProducts).extracting(DashboardTopProductProjection::getProductName)
                .containsExactly("Café", "Produto desativado");
        assertThat(topProducts).extracting(DashboardTopProductProjection::getQuantitySold)
                .containsExactly(9L, 3L);
    }

    @Test
    void shouldReturnEmptyTopProductsWhenThereAreNoCompletedSalesInPeriod() {
        TurnEntity turn = createTurn();
        ProductEntity product = createProduct("Arroz", 5, true);
        LocalDateTime start = LocalDate.of(2026, 10, 20).atStartOfDay();
        createSale(turn, "PENDING", start.plusDays(1), item(product, 2));

        assertThat(saleItemRepository.findTopProductsBetween(
                start, start.plusDays(7), PageRequest.of(0, 5))).isEmpty();
    }

    @Test
    void shouldLimitTopProductsToFiveAndBreakQuantityTiesDeterministically() {
        TurnEntity turn = createTurn();
        LocalDateTime createdAt = LocalDate.of(2026, 10, 22).atStartOfDay();
        for (String name : List.of("Produto F", "Produto E", "Produto D", "Produto C", "Produto B", "Produto A")) {
            ProductEntity product = createProduct(name, 10, true);
            createSale(turn, "COMPLETED", createdAt, item(product, 3));
        }

        List<DashboardTopProductProjection> topProducts = saleItemRepository.findTopProductsBetween(
                createdAt.minusDays(2), createdAt.plusDays(5), PageRequest.of(0, 5));

        assertThat(topProducts).hasSize(5);
        assertThat(topProducts).extracting(DashboardTopProductProjection::getProductName)
                .containsExactly("Produto A", "Produto B", "Produto C", "Produto D", "Produto E");
        assertThat(topProducts).allSatisfy(product ->
                assertThat(product.getQuantitySold()).isEqualTo(3L));
    }

    @Test
    void shouldReturnAllActiveLowStockProductsSortedByStockThenNameWithoutChangingInventory() {
        ProductEntity zeroStock = createProduct("Zerado", 0, true);
        ProductEntity fiveStockZ = createProduct("Feijão", 5, true);
        ProductEntity fiveStockA = createProduct("Açúcar", 5, true);
        createProduct("Acima do limite", 6, true);
        createProduct("Inativo", 1, false);

        List<LowStockProductProjection> lowStockProducts =
                productRepository.findLowStockProducts(5);

        assertThat(lowStockProducts).extracting(LowStockProductProjection::getName)
                .containsExactly("Zerado", "Açúcar", "Feijão");
        assertThat(lowStockProducts).extracting(LowStockProductProjection::getStock)
                .containsExactly(0, 5, 5);
        assertThat(lowStockProducts).filteredOn(product -> product.getName().equals("Açúcar"))
                .singleElement()
                .satisfies(product -> {
                    assertThat(product.getId()).isEqualTo(fiveStockA.getId());
                    assertThat(product.getBarcode()).isEqualTo(fiveStockA.getBarcode());
                });
        assertThat(lowStockProducts).filteredOn(product -> product.getName().equals("Feijão"))
                .singleElement()
                .satisfies(product -> assertThat(product.getId()).isEqualTo(fiveStockZ.getId()));
        assertThat(productRepository.findById(zeroStock.getId()).orElseThrow().getStock()).isZero();
        assertThat(productRepository.findById(fiveStockA.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void shouldReturnEmptyLowStockListWhenNoActiveProductMeetsThreshold() {
        createProduct("Estoque normal", 6, true);
        createProduct("Inativo zerado", 0, false);

        assertThat(productRepository.findLowStockProducts(5)).isEmpty();
    }

    private TurnEntity createTurn() {
        return turnRepository.saveAndFlush(TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(LocalDateTime.of(2026, 10, 20, 8, 0))
                .operatorName("Operador")
                .status(TurnStatus.OPEN)
                .build());
    }

    private ProductEntity createProduct(String name, int stock, boolean active) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 8, 0);
        return productRepository.saveAndFlush(ProductEntity.builder()
                .barcode(UUID.randomUUID().toString())
                .name(name)
                .price(new BigDecimal("10.00"))
                .stock(stock)
                .active(active)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private SaleItemEntity item(ProductEntity product, int quantity) {
        return SaleItemEntity.builder()
                .product(product)
                .quantity(quantity)
                .unitPrice(new BigDecimal("10.00"))
                .subtotal(new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    private void createSale(TurnEntity turn, String status, LocalDateTime createdAt, SaleItemEntity... items) {
        SaleEntity sale = SaleEntity.builder()
                .turn(turn)
                .status(status)
                .createdAt(createdAt)
                .total(items[0].getSubtotal())
                .build();
        saleRepository.saveAndFlush(sale);
        for (SaleItemEntity item : items) {
            item.setSale(sale);
            saleItemRepository.saveAndFlush(item);
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
