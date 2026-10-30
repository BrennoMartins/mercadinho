package br.com.aromasabor.mercadinho.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.dto.ProductRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductCatalogResponseDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductResponseDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductUpdateRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.StockAdjustmentRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.StockReplenishmentRequestDTO;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.GlobalExceptionHandler;
import br.com.aromasabor.mercadinho.product.exception.InvalidProductCatalogQueryException;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyActiveException;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.exception.ProductNotFoundException;
import br.com.aromasabor.mercadinho.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerIT {

    private MockMvc mockMvc;
    private ProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        ProductController controller = new ProductController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateProduct() throws Exception {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("7894900011517");
        request.setName("Coca-Cola 2L");
        request.setCategoryId(2L);
        request.setPrice(new BigDecimal("9.99"));
        request.setStock(20);

        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setBarcode("7894900011517");
        response.setName("Coca-Cola 2L");
        response.setPrice(new BigDecimal("9.99"));
        response.setStock(20);
        response.setActive(true);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(productService.create(any(ProductRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barcode").value("7894900011517"))
                .andExpect(jsonPath("$.name").value("Coca-Cola 2L"));
    }

    @Test
    void shouldRejectDuplicatedBarcode() throws Exception {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("1234567890123");
        request.setName("Coca-Cola 2L");
        request.setCategoryId(2L);
        request.setPrice(new BigDecimal("9.99"));
        request.setStock(20);

        when(productService.create(any(ProductRequestDTO.class)))
                .thenThrow(new ProductAlreadyExistsException("Produto já cadastrado."));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Produto já cadastrado."));
    }

    @Test
    void shouldRejectInexistentCategory() throws Exception {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("9876543210123");
        request.setName("Produto sem categoria");
        request.setCategoryId(999L);
        request.setPrice(new BigDecimal("12.50"));
        request.setStock(5);

        when(productService.create(any(ProductRequestDTO.class)))
                .thenThrow(new CategoryNotFoundException("Category not found with id: 999"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found with id: 999"));
    }

    @Test
    void shouldValidateRequiredFields() throws Exception {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("");
        request.setName("");
        request.setCategoryId(null);
        request.setPrice(new BigDecimal("-1.00"));
        request.setStock(-1);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação."))
                .andExpect(jsonPath("$.errors.barcode").value("não pode estar vazio"))
                .andExpect(jsonPath("$.errors.price").value("deve ser maior ou igual a zero"));
    }

    @Test
    void shouldListProducts() throws Exception {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setBarcode("7894900011517");
        response.setName("Coca-Cola 2L");
        response.setPrice(new BigDecimal("9.99"));
        response.setStock(20);
        response.setActive(true);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(productService.findAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].barcode").value("7894900011517"));
    }

    @Test
    void shouldListOnlyInactiveProducts() throws Exception {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(2L);
        response.setBarcode("7894900011518");
        response.setName("Produto inativo");
        response.setActive(false);
        when(productService.findInactive()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/products/inactive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].id").value(2));

        verify(productService).findInactive();
    }

    @Test
    void shouldFindProductById() throws Exception {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setBarcode("7894900011517");
        response.setName("Coca-Cola 2L");
        response.setPrice(new BigDecimal("9.99"));
        response.setStock(20);
        response.setActive(true);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(productService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldFindProductByBarcode() throws Exception {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setBarcode("7894900011517");
        response.setName("Coca-Cola 2L");
        response.setPrice(new BigDecimal("9.99"));
        response.setStock(20);
        response.setActive(true);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(productService.findByBarcode("7894900011517")).thenReturn(response);

        mockMvc.perform(get("/api/products/barcode/7894900011517"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("7894900011517"));
    }

    @Test
    void shouldSearchProductsByName() throws Exception {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setBarcode("7894900011517");
        response.setName("Coca-Cola 2L");
        response.setPrice(new BigDecimal("9.99"));
        response.setStock(20);
        response.setActive(true);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(productService.searchByName("coca")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/products/search").param("name", "coca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Coca-Cola 2L"));
    }

    @Test
    void shouldReturnAdministrativeCatalogWithDefaultPagination() throws Exception {
        ProductResponseDTO product = new ProductResponseDTO();
        product.setId(5L);
        product.setBarcode("00123");
        product.setName("Produto");
        product.setActive(true);
        ProductCatalogResponseDTO response =
                new ProductCatalogResponseDTO(List.of(product), 0, 20, 1, 1);
        when(productService.searchCatalog("produto", null, true, 0, 20)).thenReturn(response);

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "produto")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].barcode").value("00123"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(productService).searchCatalog("produto", null, true, 0, 20);
    }

    @Test
    void shouldRejectInvalidCatalogSearchAndMissingRequiredParameters() throws Exception {
        when(productService.searchCatalog(" ", null, false, 0, 20))
                .thenThrow(new InvalidProductCatalogQueryException("O parâmetro search não pode estar vazio."));

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", " ")
                        .param("active", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O parâmetro search não pode estar vazio."));

        mockMvc.perform(get("/api/products/catalog")
                        .param("search", "produto"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/products/catalog")
                        .param("active", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setBarcode("7894900011517");
        response.setName("Coca-Cola 2L");
        response.setPrice(new BigDecimal("10.50"));
        response.setStock(20);
        response.setActive(true);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(productService.update(anyLong(), any(ProductUpdateRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barcode": "7894900011517",
                                  "name": "Coca-Cola 2L",
                                  "categoryId": 2,
                                  "price": 10.50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(20));
    }

    @Test
    void shouldIgnoreUnexpectedStockFieldOnProductUpdate() throws Exception {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setName("Coca-Cola 2L");
        response.setStock(20);
        when(productService.update(anyLong(), any(ProductUpdateRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barcode": "7894900011517",
                                  "name": "Coca-Cola 2L",
                                  "categoryId": 2,
                                  "price": 10.50,
                                  "stock": 999
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(20));

        ArgumentCaptor<ProductUpdateRequestDTO> requestCaptor = ArgumentCaptor.forClass(ProductUpdateRequestDTO.class);
        verify(productService).update(eq(1L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getBarcode()).isEqualTo("7894900011517");
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReactivateProduct() throws Exception {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setBarcode("7894900011517");
        response.setName("Coca-Cola 2L");
        response.setPrice(new BigDecimal("9.99"));
        response.setStock(20);
        response.setActive(true);
        when(productService.reactivate(1L)).thenReturn(response);

        mockMvc.perform(post("/api/products/1/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.stock").value(20))
                .andExpect(jsonPath("$.barcode").value("7894900011517"));
    }

    @Test
    void shouldReturnNotFoundWhenReactivatingMissingProduct() throws Exception {
        when(productService.reactivate(99L))
                .thenThrow(new ProductNotFoundException("Product not found with id: 99"));

        mockMvc.perform(post("/api/products/99/reactivate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void shouldReturnConflictWhenReactivatingAlreadyActiveProduct() throws Exception {
        when(productService.reactivate(1L))
                .thenThrow(new ProductAlreadyActiveException("Produto já está ativo."));

        mockMvc.perform(post("/api/products/1/reactivate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Produto já está ativo."));
    }

    @Test
    void shouldReplenishProductStock() throws Exception {
        StockReplenishmentRequestDTO request = new StockReplenishmentRequestDTO(5, "Fornecedor XPTO");

        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setBarcode("7894900011517");
        response.setName("Coca-Cola 2L");
        response.setPrice(new BigDecimal("10.50"));
        response.setStock(30);
        response.setActive(true);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(productService.replenishStock(anyLong(), any(StockReplenishmentRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/products/1/replenishments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stock").value(30));
    }

    @Test
    void shouldRejectReplenishmentWithInvalidQuantity() throws Exception {
        StockReplenishmentRequestDTO request = new StockReplenishmentRequestDTO(0, "Fornecedor XPTO");

        mockMvc.perform(post("/api/products/1/replenishments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação."))
                .andExpect(jsonPath("$.errors.quantity").value("deve ser maior que zero"));
    }

    @Test
    void shouldReturnNotFoundWhenReplenishingInexistentProduct() throws Exception {
        StockReplenishmentRequestDTO request = new StockReplenishmentRequestDTO(3, "Fornecedor XPTO");

        when(productService.replenishStock(anyLong(), any(StockReplenishmentRequestDTO.class)))
                .thenThrow(new ProductNotFoundException("Product not found with id: 99"));

        mockMvc.perform(post("/api/products/99/replenishments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void shouldAdjustStockToPhysicalCount() throws Exception {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setName("Coca-Cola 2L");
        response.setStock(17);
        when(productService.adjustStock(anyLong(), any(StockAdjustmentRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/products/1/stock-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\":17,\"reason\":\"Contagem física\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(17));
    }

    @Test
    void shouldRejectInvalidStockAdjustmentRequest() throws Exception {
        mockMvc.perform(post("/api/products/1/stock-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\":-1,\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação."))
                .andExpect(jsonPath("$.errors.quantityOnHand").value("deve ser maior ou igual a zero"))
                .andExpect(jsonPath("$.errors.reason").value("não pode estar vazio"));

        mockMvc.perform(post("/api/products/1/stock-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\":1,\"reason\":\""
                                + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value("deve ter no máximo 255 caracteres"));
    }

    @Test
    void shouldReturnNotFoundWhenAdjustingInexistentProduct() throws Exception {
        when(productService.adjustStock(anyLong(), any(StockAdjustmentRequestDTO.class)))
                .thenThrow(new ProductNotFoundException("Product not found with id: 99"));

        mockMvc.perform(post("/api/products/99/stock-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\":3,\"reason\":\"Contagem física\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }
}
