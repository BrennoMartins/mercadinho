package br.com.aromasabor.mercadinho.turn.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.entity.SaleItemEntity;
import br.com.aromasabor.mercadinho.sale.repository.SaleItemRepository;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.TopSellingProductProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.TurnSalesSummaryProjection;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
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
class TurnSummaryRepositoryIT {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TurnRepository turnRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleItemRepository saleItemRepository;
    @Autowired private ProductRepository productRepository;

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
        jdbcTemplate.execute("TRUNCATE TABLE stock_movements, sale_items, sales, turns, products RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldAggregateOnlyCompletedSalesFromRequestedTurn() {
        TurnEntity requestedTurn = createTurn();
        TurnEntity otherTurn = createTurn();
        ProductEntity product = createProduct("Arroz", 10, true);
        createSale(requestedTurn, "COMPLETED", "20.00", product, 2, "10.00");
        createSale(requestedTurn, "COMPLETED", "30.00", product, 3, "10.00");
        createSale(requestedTurn, "PENDING", "90.00", product, 9, "10.00");
        createSale(otherTurn, "COMPLETED", "100.00", product, 10, "10.00");

        TurnSalesSummaryProjection summary = saleRepository.getSummaryByTurnId(requestedTurn.getId());

        assertThat(summary.getSalesCount()).isEqualTo(2L);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldAggregateItemsAcrossSalesAndOrderTopProductsDeterministically() {
        TurnEntity turn = createTurn();
        ProductEntity arroz = createProduct("Arroz", 10, true);
        ProductEntity feijao = createProduct("Feijao", 10, true);
        ProductEntity acucar = createProduct("Acucar", 10, true);
        ProductEntity biscoito = createProduct("Biscoito", 10, true);
        ProductEntity cafe = createProduct("Cafe", 10, true);
        ProductEntity leite = createProduct("Leite", 10, true);
        createSale(turn, "COMPLETED", "100.00", arroz, 6, "10.00");
        createSale(turn, "COMPLETED", "40.00", arroz, 4, "10.00");
        createSale(turn, "COMPLETED", "80.00", feijao, 10, "8.00");
        createSale(turn, "COMPLETED", "20.00", acucar, 5, "4.00");
        createSale(turn, "COMPLETED", "20.00", biscoito, 5, "4.00");
        createSale(turn, "COMPLETED", "15.00", cafe, 5, "3.00");
        createSale(turn, "COMPLETED", "10.00", leite, 2, "5.00");

        List<TopSellingProductProjection> topProducts = saleItemRepository
                .findTopSellingProductsByTurnId(turn.getId(), PageRequest.of(0, 5));

        assertThat(topProducts).hasSize(5);
        assertThat(topProducts).extracting(TopSellingProductProjection::getProductName)
                .containsExactly("Arroz", "Feijao", "Acucar", "Biscoito", "Cafe");
        assertThat(topProducts.get(0).getQuantitySold()).isEqualTo(10L);
        assertThat(topProducts.get(0).getRevenue()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldReturnAllProductsWhenExactlyFiveWereSold() {
        TurnEntity turn = createTurn();
        for (int index = 1; index <= 5; index++) {
            ProductEntity product = createProduct("Produto " + index, 10, true);
            createSale(turn, "COMPLETED", "10.00", product, index, "10.00");
        }

        List<TopSellingProductProjection> topProducts = saleItemRepository
                .findTopSellingProductsByTurnId(turn.getId(), PageRequest.of(0, 5));

        assertThat(topProducts).hasSize(5);
    }

    @Test
    void shouldCountOnlyActiveProductsAtOrBelowLowStockThreshold() {
        createProduct("No limite", 5, true);
        createProduct("Abaixo", 1, true);
        createProduct("Acima", 6, true);
        createProduct("Inativo", 1, false);

        assertThat(productRepository.countByActiveTrueAndStockLessThanEqual(5)).isEqualTo(2L);
    }

    private TurnEntity createTurn() {
        return turnRepository.save(TurnEntity.builder().id(UUID.randomUUID())
                .openedAt(LocalDateTime.now().minusHours(2)).operatorName("Maria")
                .status(TurnStatus.CLOSED).closedAt(LocalDateTime.now().minusHours(1)).build());
    }

    private ProductEntity createProduct(String name, int stock, boolean active) {
        LocalDateTime now = LocalDateTime.now();
        return productRepository.save(ProductEntity.builder().barcode(UUID.randomUUID().toString())
                .name(name).price(new BigDecimal("10.00")).stock(stock).active(active)
                .createdAt(now).updatedAt(now).build());
    }

    private void createSale(TurnEntity turn, String status, String total, ProductEntity product,
                            int quantity, String unitPrice) {
        BigDecimal price = new BigDecimal(unitPrice);
        SaleEntity sale = SaleEntity.builder().turn(turn).status(status).total(new BigDecimal(total))
                .createdAt(LocalDateTime.now()).build();
        sale.addItem(SaleItemEntity.builder().product(product).quantity(quantity).unitPrice(price)
                .subtotal(price.multiply(BigDecimal.valueOf(quantity))).build());
        saleRepository.saveAndFlush(sale);
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start embedded PostgreSQL for integration tests", ex);
        }
    }
}
