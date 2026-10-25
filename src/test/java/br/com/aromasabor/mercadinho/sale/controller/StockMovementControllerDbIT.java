package br.com.aromasabor.mercadinho.sale.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.dto.StockAdjustmentRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.StockReplenishmentRequestDTO;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.product.service.ProductService;
import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
import br.com.aromasabor.mercadinho.sale.repository.StockMovementRepository;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class StockMovementControllerDbIT {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private TurnRepository turnRepository;

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
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.execute("TRUNCATE TABLE stock_movements, sale_items, sales, turns, products RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldListSaleAndReplenishmentMovementsNewestFirst() throws Exception {
        ProductEntity product = createProduct("789000000101", "Café");
        openTurn();

        mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products/" + product.getId() + "/replenishments")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5,\"reason\":\"Nota fiscal 456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/stock-movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].productId").value(product.getId()))
                .andExpect(jsonPath("$.content[0].productName").value("Café"))
                .andExpect(jsonPath("$.content[0].movementType").value("ENTRY"))
                .andExpect(jsonPath("$.content[0].quantity").value(5))
                .andExpect(jsonPath("$.content[0].reason").value("Nota fiscal 456"))
                .andExpect(jsonPath("$.content[0].referenceSaleId").doesNotExist())
                .andExpect(jsonPath("$.content[1].movementType").value("SALE"))
                .andExpect(jsonPath("$.content[1].quantity").value(-2))
                .andExpect(jsonPath("$.content[1].reason").value("Venda #1"))
                .andExpect(jsonPath("$.content[1].referenceSaleId").value(1))
                .andExpect(jsonPath("$.content[0].createdAt").exists());
    }

    @Test
    void shouldFilterByProductTypeAndDateRange() throws Exception {
        ProductEntity firstProduct = createProduct("789000000102", "Arroz");
        ProductEntity secondProduct = createProduct("789000000103", "Feijão");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        createMovement(firstProduct, "ENTRY", 4, now.minusDays(2), "Reposição");
        createMovement(firstProduct, "ADJUSTMENT", 1, now.minusDays(1), "Ajuste registrado");
        createMovement(secondProduct, "ENTRY", 8, now, "Reposição");

        mockMvc.perform(get("/api/stock-movements")
                        .param("productId", firstProduct.getId().toString())
                        .param("movementType", "entry")
                        .param("startDate", now.minusDays(3).toString())
                        .param("endDate", now.minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].productName").value("Arroz"))
                .andExpect(jsonPath("$.content[0].movementType").value("ENTRY"))
                .andExpect(jsonPath("$.content[0].quantity").value(4));
    }

    @Test
    void shouldPaginateAndReturnEmptyHistory() throws Exception {
        ProductEntity product = createProduct("789000000104", "Leite");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        createMovement(product, "ENTRY", 1, now.minusMinutes(2), "Primeira");
        createMovement(product, "ENTRY", 2, now.minusMinutes(1), "Segunda");
        createMovement(product, "ENTRY", 3, now, "Terceira");

        mockMvc.perform(get("/api/stock-movements").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("Primeira"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/stock-movements").param("productId", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldRejectInvalidFiltersAndPagination() throws Exception {
        mockMvc.perform(get("/api/stock-movements").param("movementType", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("movementType deve ser SALE, ENTRY ou ADJUSTMENT."));

        mockMvc.perform(get("/api/stock-movements").param("productId", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/stock-movements").param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/stock-movements").param("size", "101"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/stock-movements")
                        .param("startDate", "2026-10-25T12:00:00")
                        .param("endDate", "2026-10-24T12:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAdjustStockAndPersistSignedDifferenceAndReason() throws Exception {
        ProductEntity product = createProduct("789000000105", "Farinha");

        mockMvc.perform(post("/api/products/" + product.getId() + "/stock-adjustments")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\":17,\"reason\":\"Contagem física\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(17));

        assertThat(productRepository.findById(product.getId())).get()
                .extracting(ProductEntity::getStock)
                .isEqualTo(17);
        assertThat(jdbcTemplate.queryForObject(
                "select quantity from stock_movements where product_id = ? and movement_type = 'ADJUSTMENT'",
                Integer.class,
                product.getId())).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                "select reason from stock_movements where product_id = ? and movement_type = 'ADJUSTMENT'",
                String.class,
                product.getId())).isEqualTo("Contagem física");

        mockMvc.perform(post("/api/products/" + product.getId() + "/stock-adjustments")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\":15,\"reason\":\"Perda identificada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(15));

        assertThat(jdbcTemplate.queryForList(
                "select quantity from stock_movements where product_id = ? and movement_type = 'ADJUSTMENT' "
                        + "order by id",
                Integer.class,
                product.getId())).containsExactly(7, -2);

        mockMvc.perform(get("/api/stock-movements")
                        .param("productId", product.getId().toString())
                        .param("movementType", "ADJUSTMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].quantity").value(-2))
                .andExpect(jsonPath("$.content[0].reason").value("Perda identificada"));
    }

    @Test
    void shouldRejectNoDifferenceAndNegativeFinalStock() throws Exception {
        ProductEntity product = createProduct("789000000106", "Açúcar");

        mockMvc.perform(post("/api/products/" + product.getId() + "/stock-adjustments")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\":10,\"reason\":\"Contagem\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "A quantidade informada é igual ao estoque atual; nenhum ajuste foi registrado."));

        mockMvc.perform(post("/api/products/" + product.getId() + "/stock-adjustments")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\":-1,\"reason\":\"Contagem\"}"))
                .andExpect(status().isBadRequest());

        assertThat(productRepository.findById(product.getId())).get()
                .extracting(ProductEntity::getStock)
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from stock_movements where product_id = ?",
                Integer.class,
                product.getId())).isZero();
    }

    @Test
    void shouldRollbackStockWhenAdjustmentMovementInsertFails() {
        ProductEntity product = createProduct("789000000107", "Óleo");
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_stock_adjustment_for_test() RETURNS trigger AS $$
                BEGIN
                    IF NEW.movement_type = 'ADJUSTMENT' AND NEW.reason = 'falha simulada' THEN
                        RAISE EXCEPTION 'forced stock movement failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_stock_adjustment_for_test
                BEFORE INSERT ON stock_movements
                FOR EACH ROW EXECUTE FUNCTION fail_stock_adjustment_for_test()
                """);

        try {
            assertThatThrownBy(() -> productService.adjustStock(
                    product.getId(), new StockAdjustmentRequestDTO(5, "falha simulada")))
                    .isInstanceOf(RuntimeException.class);

            assertThat(jdbcTemplate.queryForObject(
                    "select stock from products where id = ?", Integer.class, product.getId()))
                    .isEqualTo(10);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from stock_movements where product_id = ?",
                    Integer.class,
                    product.getId())).isZero();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_stock_adjustment_for_test ON stock_movements");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_stock_adjustment_for_test()");
        }
    }

    @Test
    void shouldSerializeConcurrentAdjustmentAndReplenishment() throws Exception {
        ProductEntity product = createProduct("789000000108", "Macarrão");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var adjustment = executor.submit(() -> {
                start.await();
                productService.adjustStock(
                        product.getId(), new StockAdjustmentRequestDTO(8, "Contagem física"));
                return null;
            });
            var replenishment = executor.submit(() -> {
                start.await();
                productService.replenishStock(
                        product.getId(), new StockReplenishmentRequestDTO(3, "Reposição concorrente"));
                return null;
            });

            start.countDown();
            adjustment.get(10, TimeUnit.SECONDS);
            replenishment.get(10, TimeUnit.SECONDS);
        }

        int finalStock = productRepository.findById(product.getId()).orElseThrow().getStock();
        int movementSum = stockMovementRepository.findAll().stream()
                .mapToInt(StockMovementEntity::getQuantity)
                .sum();

        assertThat(finalStock).isIn(8, 11);
        assertThat(movementSum).isEqualTo(finalStock - 10);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from stock_movements where product_id = ?",
                Integer.class,
                product.getId())).isEqualTo(2);
    }

    private ProductEntity createProduct(String barcode, String name) {
        CategoryEntity category = categoryRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Expected seeded category with id 1"));
        LocalDateTime now = LocalDateTime.now();

        return productRepository.save(ProductEntity.builder()
                .barcode(barcode)
                .name(name)
                .category(category)
                .price(new BigDecimal("10.00"))
                .stock(10)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private void createMovement(ProductEntity product,
                                String movementType,
                                int quantity,
                                LocalDateTime createdAt,
                                String reason) {
        stockMovementRepository.save(StockMovementEntity.builder()
                .product(product)
                .movementType(movementType)
                .quantity(quantity)
                .createdAt(createdAt)
                .reason(reason)
                .build());
    }

    private void openTurn() {
        turnRepository.save(TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(LocalDateTime.now().minusHours(1))
                .operatorName("Maria")
                .status(TurnStatus.OPEN)
                .openingNote("Início do expediente")
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
