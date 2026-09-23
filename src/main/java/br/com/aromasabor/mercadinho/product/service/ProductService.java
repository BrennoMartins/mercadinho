package br.com.aromasabor.mercadinho.product.service;

import br.com.aromasabor.mercadinho.product.dto.CategoryResponseDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductResponseDTO;
import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.exception.ProductAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.exception.ProductNotFoundException;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ProductResponseDTO create(ProductRequestDTO request) {
        validateBarcodeAvailability(request.getBarcode(), null);

        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + request.getCategoryId()));

        LocalDateTime now = LocalDateTime.now();

        ProductEntity product = ProductEntity.builder()
                .barcode(request.getBarcode())
                .name(request.getName())
                .category(category)
                .price(request.getPrice())
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
    public ProductResponseDTO update(Long id, ProductRequestDTO request) {
        ProductEntity product = productRepository.findById(id)
                .filter(ProductEntity::getActive)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        if (request.getBarcode() != null && !request.getBarcode().isBlank()) {
            validateBarcodeAvailability(request.getBarcode(), id);
            product.setBarcode(request.getBarcode());
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName());
        }

        if (request.getCategoryId() != null) {
            CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }

        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }

        if (request.getStock() != null) {
            product.setStock(request.getStock());
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
