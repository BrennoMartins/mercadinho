package br.com.aromasabor.mercadinho.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.category.dto.CategoryRequestDTO;
import br.com.aromasabor.mercadinho.category.dto.CategoryResponseDTO;
import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.exception.CategoryAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void shouldCreateCategorySuccessfully() {
        CategoryRequestDTO request = new CategoryRequestDTO();
        request.setName("Bebidas");

        when(categoryRepository.findByName("Bebidas")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> {
            CategoryEntity category = invocation.getArgument(0);
            category.setId(1L);
            return category;
        });

        CategoryResponseDTO response = categoryService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Bebidas");
        assertThat(response.getId()).isEqualTo(1L);
        verify(categoryRepository).save(any(CategoryEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenCategoryAlreadyExists() {
        CategoryRequestDTO request = new CategoryRequestDTO();
        request.setName("Bebidas");

        when(categoryRepository.findByName("Bebidas")).thenReturn(Optional.of(CategoryEntity.builder().id(1L).name("Bebidas").createdAt(LocalDateTime.now()).build()));

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(CategoryAlreadyExistsException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void shouldFindAllCategories() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                CategoryEntity.builder().id(1L).name("Bebidas").createdAt(LocalDateTime.now()).build(),
                CategoryEntity.builder().id(2L).name("Limpeza").createdAt(LocalDateTime.now()).build()
        ));

        List<CategoryResponseDTO> response = categoryService.findAll();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getName()).isEqualTo("Bebidas");
    }

    @Test
    void shouldFindCategoryById() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(CategoryEntity.builder().id(1L).name("Bebidas").createdAt(LocalDateTime.now()).build()));

        CategoryResponseDTO response = categoryService.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Bebidas");
    }

    @Test
    void shouldUpdateCategorySuccessfully() {
        CategoryEntity category = CategoryEntity.builder().id(1L).name("Antigo").createdAt(LocalDateTime.now()).build();
        CategoryRequestDTO request = new CategoryRequestDTO();
        request.setName("Bebidas");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByName("Bebidas")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponseDTO response = categoryService.update(1L, request);

        assertThat(response.getName()).isEqualTo("Bebidas");
    }

    @Test
    void shouldDeleteCategorySuccessfully() {
        when(categoryRepository.existsById(1L)).thenReturn(true);

        categoryService.delete(1L);

        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void shouldThrowExceptionWhenCategoryNotFoundOnDelete() {
        when(categoryRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("1");
    }
}

