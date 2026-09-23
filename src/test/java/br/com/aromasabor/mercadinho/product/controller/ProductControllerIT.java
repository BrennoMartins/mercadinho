package br.com.aromasabor.mercadinho.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.dto.ProductRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductResponseDTO;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.GlobalExceptionHandler;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void shouldUpdateProduct() throws Exception {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("7894900011517");
        request.setName("Coca-Cola 2L");
        request.setCategoryId(2L);
        request.setPrice(new BigDecimal("10.50"));
        request.setStock(25);

        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(1L);
        response.setBarcode("7894900011517");
        response.setName("Coca-Cola 2L");
        response.setPrice(new BigDecimal("10.50"));
        response.setStock(25);
        response.setActive(true);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(productService.update(anyLong(), any(ProductRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(25));
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }
}
