package br.com.aromasabor.mercadinho.category.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
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
class CategoryControllerDbIT {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

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
        jdbcTemplate.execute(
                "TRUNCATE TABLE stock_movements, sale_items, sales, turns, products, categories RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldDeleteCategoryWithoutProducts() throws Exception {
        CategoryEntity category = createCategory();

        mockMvc.perform(delete("/api/categories/" + category.getId()))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.existsById(category.getId())).isFalse();
    }

    @Test
    void shouldRejectDeletingCategoryWithActiveProductAndPreserveProductAndHistory() throws Exception {
        assertCannotDeleteCategoryWithProduct(true);
    }

    @Test
    void shouldRejectDeletingCategoryWithInactiveProductAndPreserveProductAndHistory() throws Exception {
        assertCannotDeleteCategoryWithProduct(false);
    }

    @Test
    void shouldReturnNotFoundWhenCategoryDoesNotExist() throws Exception {
        mockMvc.perform(delete("/api/categories/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found with id: 999"));
    }

    private void assertCannotDeleteCategoryWithProduct(boolean active) throws Exception {
        CategoryEntity category = createCategory();
        ProductEntity product = createProduct(category, active);
        createProductHistory(product);

        mockMvc.perform(delete("/api/categories/" + category.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Esta categoria possui produtos vinculados e não pode ser excluída."));

        assertThat(categoryRepository.existsById(category.getId())).isTrue();
        ProductEntity persistedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(persistedProduct.getCategory().getId()).isEqualTo(category.getId());
        assertThat(persistedProduct.getActive()).isEqualTo(active);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sales", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sale_items", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from stock_movements", Integer.class)).isEqualTo(1);
    }

    private CategoryEntity createCategory() {
        return categoryRepository.save(CategoryEntity.builder()
                .name("Categoria de teste")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private ProductEntity createProduct(CategoryEntity category, boolean active) {
        LocalDateTime now = LocalDateTime.now();
        return productRepository.save(ProductEntity.builder()
                .barcode(UUID.randomUUID().toString())
                .name("Produto de teste")
                .category(category)
                .price(new BigDecimal("10.00"))
                .stock(5)
                .active(active)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private void createProductHistory(ProductEntity product) {
        UUID turnId = UUID.randomUUID();
        turnRepository.save(TurnEntity.builder()
                .id(turnId)
                .openedAt(LocalDateTime.now().minusHours(1))
                .closedAt(LocalDateTime.now())
                .operatorName("Teste")
                .status(TurnStatus.CLOSED)
                .openingNote("Turno de teste")
                .build());

        Long saleId = jdbcTemplate.queryForObject(
                "insert into sales (total, subtotal, status, turn_id) values (?, ?, ?, ?) returning id",
                Long.class,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                "COMPLETED",
                turnId);
        jdbcTemplate.update(
                "insert into sale_items (sale_id, product_id, quantity, unit_price, subtotal) values (?, ?, ?, ?, ?)",
                saleId,
                product.getId(),
                1,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"));
        jdbcTemplate.update(
                "insert into stock_movements (product_id, movement_type, quantity, reason) values (?, ?, ?, ?)",
                product.getId(),
                "ENTRY",
                1,
                "Histórico de teste");
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start embedded PostgreSQL for integration tests", ex);
        }
    }
}
