package br.com.aromasabor.mercadinho.product.service;

import br.com.aromasabor.mercadinho.product.dto.CategoryResponseDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductUpdateRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductResponseDTO;
import br.com.aromasabor.mercadinho.product.dto.StockAdjustmentRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.StockReplenishmentRequestDTO;
import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyActiveException;
import br.com.aromasabor.mercadinho.product.exception.ProductNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.StockAdjustmentNotNeededException;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
import br.com.aromasabor.mercadinho.sale.repository.StockMovementRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final String MOVEMENT_TYPE_ENTRY = "ENTRY";
    private static final String MOVEMENT_TYPE_ADJUSTMENT = "ADJUSTMENT";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockMovementRepository stockMovementRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public ProductResponseDTO create(ProductRequestDTO request) {
        validateBarcodeAvailability(request.getBarcode(), null);

        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + request.getCategoryId()));

        LocalDateTime now = LocalDateTime.now();

        ProductEntity product = ProductEntity.builder()
                .barcode(request.getBarcode())
                .name(request.getName().toUpperCase(Locale.ROOT))
                .category(category)
                .price(request.getPrice())
                .costPrice(request.getCostPrice())
                .stock(request.getStock())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findAll() {
        return productRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findInactive() {
        return productRepository.findByActiveFalseOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO findById(Long id) {
        ProductEntity product = productRepository.findById(id)
                .filter(ProductEntity::getActive)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO findByBarcode(String barcode) {
        ProductEntity product = productRepository.findByBarcode(barcode)
                .filter(ProductEntity::getActive)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with barcode: " + barcode));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name).stream()
                .filter(ProductEntity::getActive)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductResponseDTO update(Long id, ProductUpdateRequestDTO request) {
        ProductEntity product = productRepository.findById(id)
                .filter(ProductEntity::getActive)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        if (request.getBarcode() != null && !request.getBarcode().isBlank()) {
            validateBarcodeAvailability(request.getBarcode(), id);
            product.setBarcode(request.getBarcode());
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName().toUpperCase(Locale.ROOT));
        }

        if (request.getCategoryId() != null) {
            CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }

        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }

        if (request.getCostPrice() != null) {
            product.setCostPrice(request.getCostPrice());
        }

        product.setUpdatedAt(LocalDateTime.now());
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        ProductEntity product = productRepository.findById(id)
                .filter(ProductEntity::getActive)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        product.setActive(false);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    @Transactional
    public ProductResponseDTO reactivate(Long id) {
        ProductEntity product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        if (Boolean.TRUE.equals(product.getActive())) {
            throw new ProductAlreadyActiveException("Produto já está ativo.");
        }

        productRepository.findByBarcode(product.getBarcode())
                .filter(existingProduct -> !existingProduct.getId().equals(id))
                .ifPresent(existingProduct -> {
                    throw new ProductAlreadyExistsException(
                            "Product with barcode '" + product.getBarcode() + "' already exists.");
                });

        product.setActive(true);
        product.setUpdatedAt(LocalDateTime.now());
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO replenishStock(Long id, StockReplenishmentRequestDTO request) {
        ProductEntity product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new ProductNotFoundException("Product not found with id: " + id);
        }

        LocalDateTime now = LocalDateTime.now();
        product.setStock(product.getStock() + request.getQuantity());
        product.setUpdatedAt(now);

        StockMovementEntity movement = StockMovementEntity.builder()
                .product(product)
                .movementType(MOVEMENT_TYPE_ENTRY)
                .quantity(request.getQuantity())
                .referenceSale(null)
                .reason(request.getReason())
                .createdAt(now)
                .build();

        stockMovementRepository.save(movement);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO adjustStock(Long id, StockAdjustmentRequestDTO request) {
        ProductEntity product = productRepository.findByIdForUpdate(id)
                .filter(ProductEntity::getActive)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        int quantityDifference = request.getQuantityOnHand() - product.getStock();
        if (quantityDifference == 0) {
            throw new StockAdjustmentNotNeededException(
                    "A quantidade informada é igual ao estoque atual; nenhum ajuste foi registrado.");
        }

        LocalDateTime now = LocalDateTime.now();
        product.setStock(request.getQuantityOnHand());
        product.setUpdatedAt(now);

        StockMovementEntity movement = StockMovementEntity.builder()
                .product(product)
                .movementType(MOVEMENT_TYPE_ADJUSTMENT)
                .quantity(quantityDifference)
                .referenceSale(null)
                .reason(request.getReason().trim())
                .createdAt(now)
                .build();

        stockMovementRepository.save(movement);
        return toResponse(productRepository.save(product));
    }

    private void validateBarcodeAvailability(String barcode, Long excludedProductId) {
        if (!productRepository.existsByBarcode(barcode)) {
            return;
        }

        if (excludedProductId == null) {
            throw new ProductAlreadyExistsException("Product with barcode '" + barcode + "' already exists.");
        }

        Long existingProductId = productRepository.findByBarcode(barcode)
                .map(ProductEntity::getId)
                .orElse(null);

        if (!excludedProductId.equals(existingProductId)) {
            throw new ProductAlreadyExistsException("Product with barcode '" + barcode + "' already exists.");
        }
    }

    private ProductResponseDTO toResponse(ProductEntity product) {
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(product.getId());
        response.setBarcode(product.getBarcode());
        response.setName(product.getName());
        response.setPrice(product.getPrice());
        response.setCostPrice(product.getCostPrice());
        response.setStock(product.getStock());
        response.setActive(product.getActive());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());

        if (product.getCategory() != null) {
            CategoryResponseDTO categoryResponse = new CategoryResponseDTO();
            categoryResponse.setId(product.getCategory().getId());
            categoryResponse.setName(product.getCategory().getName());
            categoryResponse.setCreatedAt(product.getCategory().getCreatedAt());
            response.setCategory(categoryResponse);
        }

        return response;
    }
}
