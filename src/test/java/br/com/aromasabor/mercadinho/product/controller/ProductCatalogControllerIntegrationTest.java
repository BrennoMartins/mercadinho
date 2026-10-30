package br.com.aromasabor.mercadinho.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.MercadinhoApplication;
import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDriverBasedDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
        classes = MercadinhoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(ProductCatalogControllerIntegrationTest.IsolatedDatabaseConfiguration.class)
class ProductCatalogControllerIntegrationTest {

    private static final String DATABASE_USER = "postgres";
    private static final String DATABASE_PASSWORD = "postgres";
    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        String jdbcUrl = jdbcUrl();
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> DATABASE_USER);
        registry.add("spring.datasource.password", () -> DATABASE_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", () -> DATABASE_USER);
        registry.add("spring.flyway.password", () -> DATABASE_PASSWORD);
        registry.add("spring.flyway.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void clearDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.execute("TRUNCATE TABLE products, categories RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldSearchPartialNameWithoutCaseSensitivity() throws Exception {
        createProduct("CAFÉ TORRADO", "10001", true, null);
        createProduct("Chá preto", "10002", true, null);

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "café")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("CAFÉ TORRADO"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldMatchBarcodeExactlyAndPreserveLeadingZeros() throws Exception {
        createProduct("Produto codificado", "00001234", true, null);
        createProduct("Outro código", "1234", true, null);

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "00001234")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].barcode").value("00001234"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldCombineActiveAndCategoryFilters() throws Exception {
        CategoryEntity selectedCategory = createCategory("Mercearia");
        CategoryEntity otherCategory = createCategory("Bebidas");
        createProduct("Leite desnatado", "20001", false, selectedCategory);
        createProduct("Leite integral", "20002", true, selectedCategory);
        createProduct("Leite vegetal", "20003", false, otherCategory);

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "leite")
                        .param("active", "false")
                        .param("categoryId", selectedCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].barcode").value("20001"))
                .andExpect(jsonPath("$.content[0].active").value(false))
                .andExpect(jsonPath("$.content[0].category.id").value(selectedCategory.getId()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldSortByNameAndIdAndReturnPaginationMetadata() throws Exception {
        ProductEntity first = createProduct("Leite A", "30001", true, null);
        ProductEntity second = createProduct("Leite A", "30002", true, null);
        ProductEntity third = createProduct("Leite Z", "30003", true, null);

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "leite")
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first.getId()))
                .andExpect(jsonPath("$.content[1].id").value(second.getId()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "leite")
                        .param("active", "true")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(third.getId()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void shouldUseDefaultPageAndSizeWhenOmitted() throws Exception {
        createProduct("Produto padrão", "40001", true, null);

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "produto")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void shouldReturnEmptyContentAndConsistentMetadataForPageOutsideRange() throws Exception {
        createProduct("Arroz", "50001", true, null);
        createProduct("Arroz integral", "50002", true, null);
        createProduct("Arroz parboilizado", "50003", true, null);

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "arroz")
                        .param("active", "true")
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void shouldRejectBlankSearchWithDomainErrorContract() throws Exception {
        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "   ")
                        .param("active", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O parâmetro search não pode estar vazio."));
    }

    @Test
    void shouldRejectMissingRequiredParameters() throws Exception {
        mockMvc.perform(get("/api/products/catalog")
                        .param("active", "true"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "produto"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidCatalogParameters() throws Exception {
        assertInvalidQuery("categoryId", "0",
                "O parâmetro categoryId deve ser maior que zero.");
        assertInvalidQuery("page", "-1",
                "O parâmetro page deve ser maior ou igual a zero.");
        assertInvalidQuery("size", "0",
                "O parâmetro size deve estar entre 1 e 100.");
        assertInvalidQuery("size", "101",
                "O parâmetro size deve estar entre 1 e 100.");

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "produto")
                        .param("active", "true")
                        .param("page", "nao-numero"))
                .andExpect(status().isBadRequest());
    }

    private void assertInvalidQuery(String parameter, String value, String message) throws Exception {
        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "produto")
                        .param("active", "true")
                        .param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
    }

    private CategoryEntity createCategory(String name) {
        return categoryRepository.saveAndFlush(CategoryEntity.builder()
                .name(name)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private ProductEntity createProduct(String name, String barcode, boolean active, CategoryEntity category) {
        LocalDateTime now = LocalDateTime.now();
        return productRepository.saveAndFlush(ProductEntity.builder()
                .barcode(barcode)
                .name(name)
                .category(category)
                .price(new BigDecimal("4.50"))
                .stock(7)
                .active(active)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start embedded PostgreSQL for catalog controller test", ex);
        }
    }

    private static String jdbcUrl() {
        return POSTGRES.getJdbcUrl(DATABASE_USER, DATABASE_USER);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class IsolatedDatabaseConfiguration {

        @Bean(destroyMethod = "close")
        EmbeddedPostgres embeddedPostgres() {
            return POSTGRES;
        }

        @Bean
        @Primary
        DataSource isolatedDataSource(EmbeddedPostgres postgres, Environment environment) {
            String expectedUrl = postgres.getJdbcUrl(DATABASE_USER, DATABASE_USER);
            requireEqual("spring.datasource.url", expectedUrl,
                    environment.getRequiredProperty("spring.datasource.url"));
            requireEqual("spring.datasource.username", DATABASE_USER,
                    environment.getRequiredProperty("spring.datasource.username"));
            requireEqual("spring.datasource.password", DATABASE_PASSWORD,
                    environment.getRequiredProperty("spring.datasource.password"));
            requireEqual("spring.datasource.driver-class-name", "org.postgresql.Driver",
                    environment.getRequiredProperty("spring.datasource.driver-class-name"));

            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setURL(expectedUrl);
            dataSource.setUser(DATABASE_USER);
            dataSource.setPassword(DATABASE_PASSWORD);
            return dataSource;
        }

        @Bean
        FlywayMigrationStrategy isolatedFlywayMigrationStrategy(
                EmbeddedPostgres postgres, Environment environment) {
            return flyway -> {
                String expectedUrl = postgres.getJdbcUrl(DATABASE_USER, DATABASE_USER);
                requireEqual("spring.flyway.url", expectedUrl,
                        environment.getRequiredProperty("spring.flyway.url"));
                requireEqual("spring.flyway.user", DATABASE_USER,
                        environment.getRequiredProperty("spring.flyway.user"));
                requireEqual("spring.flyway.password", DATABASE_PASSWORD,
                        environment.getRequiredProperty("spring.flyway.password"));
                requireEqual("spring.flyway.driver-class-name", "org.postgresql.Driver",
                        environment.getRequiredProperty("spring.flyway.driver-class-name"));
                if (!(flyway.getConfiguration().getDataSource() instanceof AbstractDriverBasedDataSource dataSource)) {
                    throw new IllegalStateException("Flyway datasource cannot be verified without connecting");
                }
                requireEqual("Flyway datasource URL", expectedUrl, dataSource.getUrl());
                requireEqual("Flyway datasource user", DATABASE_USER, dataSource.getUsername());
                requireEqual("Flyway datasource password", DATABASE_PASSWORD, dataSource.getPassword());
                flyway.migrate();
            };
        }

        private static void requireEqual(String property, String expected, String actual) {
            if (!expected.equals(actual)) {
                throw new IllegalStateException(property + " does not point to the embedded test database");
            }
        }
    }
}
