package br.com.aromasabor.mercadinho.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.product.dto.ProductRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductResponseDTO;
import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldCreateProductSuccessfully() {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("7894900011517");
        request.setName("Coca-Cola 2L");
        request.setCategoryId(2L);
        request.setPrice(new BigDecimal("9.99"));
        request.setStock(20);

        CategoryEntity category = CategoryEntity.builder()
                .id(2L)
                .name("Bebidas")
                .createdAt(LocalDateTime.now())
                .build();

        when(productRepository.existsByBarcode("7894900011517")).thenReturn(false);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity product = invocation.getArgument(0);
            product.setId(1L);
            return product;
        });

        ProductResponseDTO response = productService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getBarcode()).isEqualTo("7894900011517");
        assertThat(response.getName()).isEqualTo("Coca-Cola 2L");
        assertThat(response.getCategory()).isNotNull();
        assertThat(response.getCategory().getId()).isEqualTo(2L);
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("9.99"));
        assertThat(response.getStock()).isEqualTo(20);
        assertThat(response.getActive()).isTrue();
        verify(productRepository).save(any(ProductEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenBarcodeAlreadyExists() {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("7894900011517");
        request.setName("Coca-Cola 2L");
        request.setCategoryId(2L);
        request.setPrice(new BigDecimal("9.99"));
        request.setStock(20);

        when(productRepository.existsByBarcode("7894900011517")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenCategoryDoesNotExist() {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("7894900011517");
        request.setName("Coca-Cola 2L");
        request.setCategoryId(99L);
        request.setPrice(new BigDecimal("9.99"));
        request.setStock(20);

        when(productRepository.existsByBarcode("7894900011517")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never()).save(any(ProductEntity.class));
    }
}

