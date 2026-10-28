package br.com.aromasabor.mercadinho.sale.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.sale.service.SaleService;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.dto.CloseTurnRequest;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import br.com.aromasabor.mercadinho.turn.service.TurnService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class SaleControllerDbIT {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TurnRepository turnRepository;

    @Autowired
    private SaleService saleService;

    @Autowired
    private TurnService turnService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    void shouldCompleteTurnLifecycleAndKeepSaleAssociatedWithClosedTurn() throws Exception {
        ProductEntity product = createActiveProduct("789000000012");
        mockMvc.perform(get("/api/turns/current"))
                .andExpect(status().isNotFound());

        String openRequest = """
                {"operatorName":"Maria","openingNote":"Abertura de teste"}
                """;
        MvcResult openResult = mockMvc.perform(post("/api/turns/open")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(openRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        UUID turnId = UUID.fromString(objectMapper.readTree(openResult.getResponse().getContentAsString())
                .get("id").asText());

        mockMvc.perform(get("/api/turns/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(turnId.toString()));
        mockMvc.perform(post("/api/turns/open")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(openRequest))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/turns/" + turnId + "/close")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"closingNote\":\"Fechamento de teste\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(get("/api/turns/" + turnId + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnId").value(turnId.toString()))
                .andExpect(jsonPath("$.salesCount").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(20.00))
                .andExpect(jsonPath("$.topProducts[0].productId").value(product.getId()));

        mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":1}]}"))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject(
                "select turn_id from sales", UUID.class)).isEqualTo(turnId);
        assertThat(productRepository.findById(product.getId())).get()
                .extracting(ProductEntity::getStock)
                .isEqualTo(8);
    }

    @Test
    void shouldExposeHistoricalGrossProfitAndKeepLegacyCostUnavailableAfterCatalogChanges() throws Exception {
        TurnEntity turn = openTurn();
        ProductEntity product = createActiveProduct("789000000125");
        jdbcTemplate.update("update products set cost_price = 6.00 where id = ?", product.getId());

        MvcResult saleResult = mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grossProfitKnownAmount").value(8.00))
                .andExpect(jsonPath("$.grossProfitComplete").value(true))
                .andExpect(jsonPath("$.itemsWithoutHistoricalCost").value(0))
                .andExpect(jsonPath("$.total").value(20.00))
                .andReturn();
        long saleId = objectMapper.readTree(saleResult.getResponse().getContentAsString()).get("id").asLong();

        jdbcTemplate.update("update products set cost_price = 1.00 where id = ?", product.getId());
        mockMvc.perform(get("/api/sales/" + saleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossProfitKnownAmount").value(8.00))
                .andExpect(jsonPath("$.grossProfitComplete").value(true))
                .andExpect(jsonPath("$.total").value(20.00));
        mockMvc.perform(get("/api/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].grossProfitKnownAmount").value(8.00))
                .andExpect(jsonPath("$[0].grossProfitComplete").value(true));

        jdbcTemplate.update("""
                INSERT INTO sales (turn_id, status, total, created_at)
                VALUES (?, 'COMPLETED', 10.00, ?)
                """, turn.getId(), LocalDateTime.now().minusDays(10));
        Long legacySaleId = jdbcTemplate.queryForObject("SELECT max(id) FROM sales", Long.class);
        jdbcTemplate.update("""
                INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal)
                VALUES (?, ?, 1, 10.00, 10.00)
                """, legacySaleId, product.getId());

        mockMvc.perform(get("/api/sales/" + legacySaleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossProfitKnownAmount").value(0.00))
                .andExpect(jsonPath("$.grossProfitComplete").value(false))
                .andExpect(jsonPath("$.itemsWithoutHistoricalCost").value(1));
    }

    @Test
    void shouldWaitForInFlightSaleTurnReadBeforeClosingTurn() throws Exception {
        TurnEntity turn = openTurn();
        CountDownLatch sharedLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseSharedLock = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var saleTransaction = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        turnRepository.findFirstByStatusForShare(TurnStatus.OPEN).orElseThrow();
                        sharedLockAcquired.countDown();
                        try {
                            if (!releaseSharedLock.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out waiting to release shared turn lock.");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while holding shared turn lock.", exception);
                        }
                    }));

            assertThat(sharedLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            var closeTransaction = executor.submit(() ->
                    turnService.close(turn.getId(), new CloseTurnRequest("Fim do expediente")));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean closeIsWaitingForLock = false;
            while (System.nanoTime() < deadline) {
                closeIsWaitingForLock = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                        select exists (
                            select 1
                            from pg_stat_activity
                            where datname = current_database()
                              and pid <> pg_backend_pid()
                              and wait_event_type = 'Lock'
                              and query ilike '%turns%'
                        )
                        """, Boolean.class));
                if (closeIsWaitingForLock) {
                    break;
                }
                Thread.sleep(10);
            }

            assertThat(closeIsWaitingForLock).isTrue();
            releaseSharedLock.countDown();
            saleTransaction.get(5, TimeUnit.SECONDS);
            assertThat(closeTransaction.get(5, TimeUnit.SECONDS).getStatus().name()).isEqualTo("CLOSED");
        } finally {
            releaseSharedLock.countDown();
        }
    }

    @Test
    void shouldPersistTurnIdWhenCreatingSaleWithOpenTurn() throws Exception {
        ProductEntity product = createActiveProduct("789000000001");
        TurnEntity openTurn = openTurn();

        SaleRequestDTO request = new SaleRequestDTO(
                java.util.List.of(new SaleItemRequestDTO(product.getId(), 2))
        );

        MvcResult result = mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        long saleId = response.get("id").asLong();

        UUID persistedTurnId = jdbcTemplate.queryForObject(
                "select turn_id from sales where id = ?",
                UUID.class,
                saleId
        );
        Integer saleItemsCount = jdbcTemplate.queryForObject(
                "select count(*) from sale_items where sale_id = ?",
                Integer.class,
                saleId
        );
        Integer stockMovementsCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_movements where reference_sale_id = ?",
                Integer.class,
                saleId
        );

        assertThat(persistedTurnId).isEqualTo(openTurn.getId());
        assertThat(saleItemsCount).isEqualTo(1);
        assertThat(stockMovementsCount).isEqualTo(1);
        assertThat(productRepository.findById(product.getId())).get()
                .extracting(ProductEntity::getStock)
                .isEqualTo(8);
    }

    @Test
    void shouldRejectSaleWhenThereIsNoOpenTurnInDatabase() throws Exception {
        ProductEntity product = createActiveProduct("789000000002");

        SaleRequestDTO request = new SaleRequestDTO(
                java.util.List.of(new SaleItemRequestDTO(product.getId(), 1))
        );

        mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Não é possível registrar a venda porque o mercado está fechado."));

        Integer persistedSales = jdbcTemplate.queryForObject("select count(*) from sales", Integer.class);
        assertThat(persistedSales).isZero();
    }

    @Test
    void shouldRejectInsufficientStockAndInactiveProductsWithoutPartialSale() throws Exception {
        ProductEntity firstProduct = createActiveProduct("789000000013");
        ProductEntity secondProduct = createActiveProduct("789000000014");
        openTurn();
        jdbcTemplate.update("update products set stock = 1 where id = ?", secondProduct.getId());

        mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + firstProduct.getId()
                                + ",\"quantity\":1},{\"productId\":" + secondProduct.getId()
                                + ",\"quantity\":2}]}"))
                .andExpect(status().isConflict());

        assertThat(productRepository.findById(firstProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(productRepository.findById(secondProduct.getId()).orElseThrow().getStock()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sales", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from stock_movements", Integer.class)).isZero();

        mockMvc.perform(delete("/api/products/" + firstProduct.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + firstProduct.getId() + ",\"quantity\":1}]}"))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("select count(*) from sales", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from stock_movements", Integer.class)).isZero();
    }

    @Test
    void shouldRollbackStockAndSaleWhenSaleMovementInsertFails() {
        ProductEntity product = createActiveProduct("789000000015");
        openTurn();
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_sale_movement_for_test() RETURNS trigger AS $$
                BEGIN
                    IF NEW.movement_type = 'SALE' THEN
                        RAISE EXCEPTION 'forced sale movement failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_sale_movement_for_test
                BEFORE INSERT ON stock_movements
                FOR EACH ROW EXECUTE FUNCTION fail_sale_movement_for_test()
                """);

        try {
            assertThatThrownBy(() -> saleService.create(new SaleRequestDTO(
                    java.util.List.of(new SaleItemRequestDTO(product.getId(), 2)))))
                    .isInstanceOf(RuntimeException.class);

            assertThat(jdbcTemplate.queryForObject(
                    "select stock from products where id = ?", Integer.class, product.getId())).isEqualTo(10);
            assertThat(jdbcTemplate.queryForObject("select count(*) from sales", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("select count(*) from sale_items", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("select count(*) from stock_movements", Integer.class)).isZero();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_sale_movement_for_test ON stock_movements");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_sale_movement_for_test()");
        }
    }

    @Test
    void shouldPreserveHistoryAndAllowSaleAfterProductReactivation() throws Exception {
        ProductEntity product = createActiveProduct("789000000003");
        ProductEntity otherActiveProduct = createActiveProduct("789000000004");
        openTurn();
        String saleRequest = objectMapper.writeValueAsString(new SaleRequestDTO(
                java.util.List.of(new SaleItemRequestDTO(product.getId(), 2))));

        mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(saleRequest))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/products/" + product.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherActiveProduct.getId()));
        mockMvc.perform(get("/api/products/inactive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(product.getId()))
                .andExpect(jsonPath("$[0].active").value(false));

        mockMvc.perform(post("/api/products/" + product.getId() + "/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.stock").value(8))
                .andExpect(jsonPath("$.barcode").value("789000000003"))
                .andExpect(jsonPath("$.name").value("Arroz"))
                .andExpect(jsonPath("$.price").value(10.00));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sale_items where product_id = ?", Integer.class, product.getId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from stock_movements where product_id = ?", Integer.class, product.getId()))
                .isEqualTo(1);

        String subsequentSaleRequest = objectMapper.writeValueAsString(new SaleRequestDTO(
                java.util.List.of(new SaleItemRequestDTO(product.getId(), 1))));
        mockMvc.perform(post("/api/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(subsequentSaleRequest))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sale_items where product_id = ?", Integer.class, product.getId()))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from stock_movements where product_id = ?", Integer.class, product.getId()))
                .isEqualTo(2);
        assertThat(productRepository.findById(product.getId())).get()
                .extracting(ProductEntity::getStock)
                .isEqualTo(7);
    }

    private ProductEntity createActiveProduct(String barcode) {
        CategoryEntity category = categoryRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Expected seeded category with id 1"));
        LocalDateTime now = LocalDateTime.now();

        ProductEntity product = ProductEntity.builder()
                .barcode(barcode)
                .name("Arroz")
                .category(category)
                .price(new BigDecimal("10.00"))
                .stock(10)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return productRepository.save(product);
    }

    private TurnEntity openTurn() {
        return turnRepository.save(TurnEntity.builder()
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
