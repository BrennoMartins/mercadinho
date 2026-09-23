package br.com.aromasabor.mercadinho.category.controller;

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

import br.com.aromasabor.mercadinho.category.dto.CategoryRequestDTO;
import br.com.aromasabor.mercadinho.category.dto.CategoryResponseDTO;
import br.com.aromasabor.mercadinho.category.service.CategoryService;
import br.com.aromasabor.mercadinho.product.exception.CategoryAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CategoryControllerIT {

    private MockMvc mockMvc;
    private CategoryService categoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        categoryService = mock(CategoryService.class);
        CategoryController controller = new CategoryController(categoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateCategory() throws Exception {
        CategoryRequestDTO request = new CategoryRequestDTO();
        request.setName("Bebidas");

        CategoryResponseDTO response = new CategoryResponseDTO();
        response.setId(1L);
        response.setName("Bebidas");
        response.setCreatedAt(LocalDateTime.now());

        when(categoryService.create(any(CategoryRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bebidas"));
    }

    @Test
    void shouldListCategories() throws Exception {
        CategoryResponseDTO response = new CategoryResponseDTO();
        response.setId(1L);
        response.setName("Bebidas");
        response.setCreatedAt(LocalDateTime.now());

        when(categoryService.findAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bebidas"));
    }

    @Test
    void shouldFindCategoryById() throws Exception {
        CategoryResponseDTO response = new CategoryResponseDTO();
        response.setId(1L);
        response.setName("Bebidas");
        response.setCreatedAt(LocalDateTime.now());

        when(categoryService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldUpdateCategory() throws Exception {
        CategoryRequestDTO request = new CategoryRequestDTO();
        request.setName("Bebidas");

        CategoryResponseDTO response = new CategoryResponseDTO();
        response.setId(1L);
        response.setName("Bebidas");
        response.setCreatedAt(LocalDateTime.now());

        when(categoryService.update(anyLong(), any(CategoryRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bebidas"));
    }

    @Test
    void shouldDeleteCategory() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectDuplicateCategory() throws Exception {
        CategoryRequestDTO request = new CategoryRequestDTO();
        request.setName("Bebidas");

        when(categoryService.create(any(CategoryRequestDTO.class)))
                .thenThrow(new CategoryAlreadyExistsException("Category with name 'Bebidas' already exists."));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Categoria já cadastrada."));
    }

    @Test
    void shouldRejectCategoryNotFound() throws Exception {
        when(categoryService.findById(99L))
                .thenThrow(new CategoryNotFoundException("Category not found with id: 99"));

        mockMvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found with id: 99"));
    }
}
