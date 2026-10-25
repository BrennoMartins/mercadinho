package br.com.aromasabor.mercadinho.sale.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
import br.com.aromasabor.mercadinho.sale.repository.StockMovementRepository;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
