package br.com.aromasabor.mercadinho.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.product.dto.ProductRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductResponseDTO;
import br.com.aromasabor.mercadinho.product.dto.StockReplenishmentRequestDTO;
import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.exception.ProductNotFoundException;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
import br.com.aromasabor.mercadinho.sale.repository.StockMovementRepository;
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

    @Mock
    private StockMovementRepository stockMovementRepository;

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

    @Test
    void shouldReplenishStockAndCreateEntryMovement() {
        ProductEntity product = ProductEntity.builder()
                .id(10L)
                .barcode("78910")
                .name("Arroz")
                .price(new BigDecimal("10.00"))
                .stock(5)
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        StockReplenishmentRequestDTO request = new StockReplenishmentRequestDTO(4, "Compra fornecedor");

        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(stockMovementRepository.save(any(StockMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponseDTO response = productService.replenishStock(10L, request);

        assertThat(response.getStock()).isEqualTo(9);

        verify(stockMovementRepository).save(any(StockMovementEntity.class));
        verify(productRepository).save(product);
    }

    @Test
    void shouldRejectReplenishmentWhenProductDoesNotExist() {
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.replenishStock(99L, new StockReplenishmentRequestDTO(2, null)))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        verify(stockMovementRepository, never()).save(any(StockMovementEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void shouldRollbackWhenStockMovementPersistenceFails() {
        ProductEntity product = ProductEntity.builder()
                .id(11L)
                .barcode("78911")
                .name("Feijao")
                .price(new BigDecimal("8.00"))
                .stock(7)
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(productRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(product));
        when(stockMovementRepository.save(any(StockMovementEntity.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> productService.replenishStock(11L, new StockReplenishmentRequestDTO(3, "NF 123")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(productRepository, never()).save(any(ProductEntity.class));
    }
}

