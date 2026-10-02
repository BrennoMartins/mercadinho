package br.com.aromasabor.mercadinho.sale.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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




