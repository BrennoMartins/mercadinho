package br.com.aromasabor.mercadinho.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aromasabor.mercadinho.product.dto.ProductCatalogResponseDTO;
import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.service.ProductService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ProductCatalogRepositoryTest {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
        jdbcTemplate.execute("TRUNCATE TABLE products, categories RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldFindPartialProductNameWithoutMatchingCase() {
        createProduct("CAFÉ TORRADO", "10001", true, null);

        ProductCatalogResponseDTO response = productService.searchCatalog("café", null, true, 0, 20);

        assertThat(response.getContent()).extracting("name").containsExactly("CAFÉ TORRADO");
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldMatchBarcodeExactlyAndPreserveLeadingZeros() {
        createProduct("Produto codificado", "00001234", true, null);
        createProduct("Outro código", "1234", true, null);

        ProductCatalogResponseDTO response = productService.searchCatalog("00001234", null, true, 0, 20);

        assertThat(response.getContent()).extracting("barcode").containsExactly("00001234");
        assertThat(productService.searchCatalog("1234", null, true, 0, 20).getContent())
                .extracting("barcode")
                .containsExactly("1234");
    }

    @Test
    void shouldCombineCategoryAndStatusFiltersAndExcludeNonMatchingProducts() {
        CategoryEntity selectedCategory = createCategory("Mercearia");
        CategoryEntity otherCategory = createCategory("Bebidas");
        createProduct("Leite desnatado", "20001", false, selectedCategory);
        createProduct("Leite integral", "20002", true, selectedCategory);
        createProduct("Leite vegetal", "20003", false, otherCategory);
        createProduct("Creme de leite", "20004", false, otherCategory);

        ProductCatalogResponseDTO response =
                productService.searchCatalog("leite", selectedCategory.getId(), false, 0, 20);

        assertThat(response.getContent()).extracting("barcode").containsExactly("20001");
        assertThat(response.getContent()).allMatch(product -> Boolean.FALSE.equals(product.getActive()));
    }

    @Test
    void shouldReturnOneProductWhenNameAndBarcodeBothMatch() {
        createProduct("Produto 00123", "00123", true, null);

        ProductCatalogResponseDTO response = productService.searchCatalog("00123", null, true, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldTreatLikeWildcardsAsLiteralSearchCharacters() {
        createProduct("Produto 100% Natural", "30001", true, null);
        createProduct("Produto 1000 Natural", "30002", true, null);

        ProductCatalogResponseDTO response = productService.searchCatalog("100%", null, true, 0, 20);

        assertThat(response.getContent()).extracting("barcode").containsExactly("30001");
    }

    @Test
    void shouldSortByNameAndUseIdAsDeterministicTieBreakerAcrossPages() {
        ProductEntity first = createProduct("Leite", "40001", true, null);
        ProductEntity second = createProduct("Leite", "40002", true, null);
        ProductEntity third = createProduct("Leite", "40003", true, null);

        ProductCatalogResponseDTO firstPage = productService.searchCatalog("leite", null, true, 0, 2);
        ProductCatalogResponseDTO secondPage = productService.searchCatalog("leite", null, true, 1, 2);

        assertThat(firstPage.getContent()).extracting("id").containsExactly(first.getId(), second.getId());
        assertThat(secondPage.getContent()).extracting("id").containsExactly(third.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getPage()).isEqualTo(0);
        assertThat(firstPage.getSize()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyContentAndConsistentMetadataForPageOutsideRange() {
        createProduct("Arroz", "50001", true, null);
        createProduct("Arroz integral", "50002", true, null);
        createProduct("Arroz parboilizado", "50003", true, null);

        ProductCatalogResponseDTO response = productService.searchCatalog("arroz", null, true, 2, 2);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(2);
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
            throw new IllegalStateException("Could not start embedded PostgreSQL for product catalog test", ex);
        }
    }
}
