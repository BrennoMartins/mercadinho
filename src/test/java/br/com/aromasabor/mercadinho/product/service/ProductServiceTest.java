package br.com.aromasabor.mercadinho.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.product.dto.ProductRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductResponseDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductUpdateRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.StockAdjustmentRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.StockReplenishmentRequestDTO;
import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyActiveException;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.exception.ProductNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.StockAdjustmentNotNeededException;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
import br.com.aromasabor.mercadinho.sale.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        request.setCostPrice(new BigDecimal("6.25"));
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
        assertThat(response.getName()).isEqualTo("COCA-COLA 2L");
        assertThat(response.getCategory()).isNotNull();
        assertThat(response.getCategory().getId()).isEqualTo(2L);
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("9.99"));
        assertThat(response.getCostPrice()).isEqualByComparingTo("6.25");
        assertThat(response.getStock()).isEqualTo(20);
        assertThat(response.getActive()).isTrue();
        verify(productRepository).save(any(ProductEntity.class));
    }

    @Test
    void shouldPersistLowercaseProductNameInUppercaseAndPreserveAccentsAndSymbols() {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("7894900011518");
        request.setName("café-cola 2 litros");
        request.setCategoryId(2L);
        request.setPrice(new BigDecimal("9.99"));
        request.setStock(20);

        CategoryEntity category = CategoryEntity.builder()
                .id(2L)
                .name("Bebidas")
                .createdAt(LocalDateTime.now())
                .build();

        when(productRepository.existsByBarcode("7894900011518")).thenReturn(false);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity product = invocation.getArgument(0);
            product.setId(2L);
            return product;
        });

        ProductResponseDTO response = productService.create(request);
        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);

        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getName()).isEqualTo("CAFÉ-COLA 2 LITROS");
        assertThat(response.getName()).isEqualTo("CAFÉ-COLA 2 LITROS");
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
    void shouldListInactiveProductsInRepositoryOrder() {
        ProductEntity inactiveProduct = ProductEntity.builder()
                .id(3L)
                .barcode("78913")
                .name("Produto inativo")
                .price(new BigDecimal("4.50"))
                .stock(7)
                .active(false)
                .build();
        ProductEntity earlierInactiveProduct = ProductEntity.builder()
                .id(2L)
                .barcode("78912")
                .name("Outro produto inativo")
                .price(new BigDecimal("3.50"))
                .stock(4)
                .active(false)
                .build();
        when(productRepository.findByActiveFalseOrderByIdAsc())
                .thenReturn(List.of(earlierInactiveProduct, inactiveProduct));

        List<ProductResponseDTO> response = productService.findInactive();

        assertThat(response).extracting(ProductResponseDTO::getId).containsExactly(2L, 3L);
        assertThat(response).allMatch(product -> Boolean.FALSE.equals(product.getActive()));
        assertThat(response.getLast().getStock()).isEqualTo(7);
        verify(productRepository).findByActiveFalseOrderByIdAsc();
    }

    @Test
    void shouldReactivateProductWithoutChangingItsDataOrStockHistory() {
        CategoryEntity category = CategoryEntity.builder()
                .id(2L)
                .name("Bebidas")
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(3);
        ProductEntity product = ProductEntity.builder()
                .id(17L)
                .barcode("78917")
                .name("Suco")
                .category(category)
                .price(new BigDecimal("6.25"))
                .stock(8)
                .active(false)
                .createdAt(createdAt)
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(productRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(product));
        when(productRepository.findByBarcode("78917")).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponseDTO response = productService.reactivate(17L);

        assertThat(response.getActive()).isTrue();
        assertThat(response.getStock()).isEqualTo(8);
        assertThat(response.getBarcode()).isEqualTo("78917");
        assertThat(response.getName()).isEqualTo("Suco");
        assertThat(response.getCategory().getId()).isEqualTo(2L);
        assertThat(response.getPrice()).isEqualByComparingTo("6.25");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        verify(productRepository).save(product);
        verify(stockMovementRepository, never()).save(any(StockMovementEntity.class));
        verify(stockMovementRepository, never()).saveAll(any());
    }

    @Test
    void shouldRejectReactivationWhenProductDoesNotExist() {
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.reactivate(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void shouldRejectReactivationWhenProductIsAlreadyActive() {
        ProductEntity product = ProductEntity.builder()
                .id(18L)
                .active(true)
                .build();
        when(productRepository.findByIdForUpdate(18L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.reactivate(18L))
                .isInstanceOf(ProductAlreadyActiveException.class)
                .hasMessage("Produto já está ativo.");

        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void shouldRejectReactivationWhenBarcodeBelongsToAnotherProduct() {
        ProductEntity inactiveProduct = ProductEntity.builder()
                .id(19L)
                .barcode("78919")
                .active(false)
                .build();
        ProductEntity conflictingProduct = ProductEntity.builder()
                .id(20L)
                .barcode("78919")
                .active(true)
                .build();
        when(productRepository.findByIdForUpdate(19L)).thenReturn(Optional.of(inactiveProduct));
        when(productRepository.findByBarcode("78919")).thenReturn(Optional.of(conflictingProduct));

        assertThatThrownBy(() -> productService.reactivate(19L))
                .isInstanceOf(ProductAlreadyExistsException.class);

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

    @Test
    void shouldAdjustStockUpAndPersistPositiveDifferenceAndReason() {
        ProductEntity product = ProductEntity.builder()
                .id(12L)
                .name("Arroz")
                .stock(10)
                .active(true)
                .build();
        when(productRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponseDTO response = productService.adjustStock(
                12L, new StockAdjustmentRequestDTO(17, "Contagem física"));

        ArgumentCaptor<StockMovementEntity> movementCaptor = ArgumentCaptor.forClass(StockMovementEntity.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(response.getStock()).isEqualTo(17);
        assertThat(product.getStock()).isEqualTo(17);
        assertThat(movementCaptor.getValue().getMovementType()).isEqualTo("ADJUSTMENT");
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(7);
        assertThat(movementCaptor.getValue().getReason()).isEqualTo("Contagem física");
        verify(productRepository).save(product);
    }

    @Test
    void shouldAdjustStockDownAndPersistNegativeDifference() {
        ProductEntity product = ProductEntity.builder()
                .id(13L)
                .name("Feijão")
                .stock(20)
                .active(true)
                .build();
        when(productRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponseDTO response = productService.adjustStock(
                13L, new StockAdjustmentRequestDTO(17, "Perda identificada"));

        ArgumentCaptor<StockMovementEntity> movementCaptor = ArgumentCaptor.forClass(StockMovementEntity.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(response.getStock()).isEqualTo(17);
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(-3);
        assertThat(movementCaptor.getValue().getReason()).isEqualTo("Perda identificada");
    }

    @Test
    void shouldRejectAdjustmentWithoutStockDifference() {
        ProductEntity product = ProductEntity.builder()
                .id(14L)
                .stock(10)
                .active(true)
                .build();
        when(productRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.adjustStock(
                14L, new StockAdjustmentRequestDTO(10, "Contagem física")))
                .isInstanceOf(StockAdjustmentNotNeededException.class);

        verify(stockMovementRepository, never()).save(any(StockMovementEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void shouldRejectAdjustmentForMissingProduct() {
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.adjustStock(
                99L, new StockAdjustmentRequestDTO(5, "Contagem física")))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        verify(stockMovementRepository, never()).save(any(StockMovementEntity.class));
    }

    @Test
    void shouldUpdateProductDetailsWithoutChangingStockOrCreatingMovement() {
        CategoryEntity category = CategoryEntity.builder()
                .id(2L)
                .name("Bebidas")
                .createdAt(LocalDateTime.now())
                .build();
        ProductEntity product = ProductEntity.builder()
                .id(16L)
                .barcode("78916")
                .name("Café")
                .category(category)
                .price(new BigDecimal("10.00"))
                .stock(12)
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        ProductUpdateRequestDTO request = new ProductUpdateRequestDTO(
                "78916", "Café especial 2 Tipo A", 2L, new BigDecimal("12.50"), new BigDecimal("8.40"));

        when(productRepository.findById(16L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponseDTO response = productService.update(16L, request);

        assertThat(response.getName()).isEqualTo("CAFÉ ESPECIAL 2 TIPO A");
        assertThat(product.getName()).isEqualTo("CAFÉ ESPECIAL 2 TIPO A");
        assertThat(response.getPrice()).isEqualByComparingTo("12.50");
        assertThat(response.getCostPrice()).isEqualByComparingTo("8.40");
        assertThat(product.getCostPrice()).isEqualByComparingTo("8.40");
        assertThat(response.getStock()).isEqualTo(12);
        assertThat(product.getStock()).isEqualTo(12);
        verify(stockMovementRepository, never()).save(any(StockMovementEntity.class));
        verify(stockMovementRepository, never()).saveAll(any());
    }

    @Test
    void shouldPreserveExistingCostWhenUpdateDoesNotIncludeCostPrice() {
        CategoryEntity category = CategoryEntity.builder()
                .id(2L)
                .name("Bebidas")
                .createdAt(LocalDateTime.now())
                .build();
        ProductEntity product = ProductEntity.builder()
                .id(17L)
                .barcode("78917")
                .name("Suco")
                .category(category)
                .price(new BigDecimal("10.00"))
                .costPrice(new BigDecimal("4.75"))
                .stock(8)
                .active(true)
                .build();
        ProductUpdateRequestDTO request = new ProductUpdateRequestDTO(
                "78917", "Suco atualizado", 2L, new BigDecimal("11.00"), null);

        when(productRepository.findById(17L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponseDTO response = productService.update(17L, request);

        assertThat(response.getCostPrice()).isEqualByComparingTo("4.75");
        assertThat(product.getCostPrice()).isEqualByComparingTo("4.75");
    }

    @Test
    void shouldAllowProductWithoutKnownCostPrice() {
        ProductRequestDTO request = new ProductRequestDTO();
        request.setBarcode("7894900011519");
        request.setName("Produto sem custo conhecido");
        request.setCategoryId(2L);
        request.setPrice(new BigDecimal("9.99"));
        request.setStock(1);

        CategoryEntity category = CategoryEntity.builder()
                .id(2L)
                .name("Bebidas")
                .createdAt(LocalDateTime.now())
                .build();

        when(productRepository.existsByBarcode("7894900011519")).thenReturn(false);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity product = invocation.getArgument(0);
            product.setId(3L);
            return product;
        });

        ProductResponseDTO response = productService.create(request);

        assertThat(response.getCostPrice()).isNull();
    }

    @Test
    void shouldNotSaveProductWhenAdjustmentMovementPersistenceFails() {
        ProductEntity product = ProductEntity.builder()
                .id(15L)
                .stock(10)
                .active(true)
                .build();
        when(productRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(product));
        when(stockMovementRepository.save(any(StockMovementEntity.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> productService.adjustStock(
                15L, new StockAdjustmentRequestDTO(8, "Contagem física")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(productRepository, never()).save(any(ProductEntity.class));
    }
}
