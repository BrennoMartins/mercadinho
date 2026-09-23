package br.com.aromasabor.mercadinho.category.service;

import br.com.aromasabor.mercadinho.category.dto.CategoryRequestDTO;
import br.com.aromasabor.mercadinho.category.dto.CategoryResponseDTO;
import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.exception.CategoryAlreadyExistsException;
import br.com.aromasabor.mercadinho.product.exception.CategoryNotFoundException;
import br.com.aromasabor.mercadinho.product.repository.CategoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public CategoryResponseDTO create(CategoryRequestDTO request) {
        String normalizedName = request.getName() == null ? null : request.getName().trim();
        validateNameAvailability(normalizedName, null);

        CategoryEntity category = CategoryEntity.builder()
                .name(normalizedName)
                .createdAt(LocalDateTime.now())
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> findAll() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO findById(Long id) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + id));
        return toResponse(category);
    }

    @Transactional
    public CategoryResponseDTO update(Long id, CategoryRequestDTO request) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + id));

        String normalizedName = request.getName() == null ? null : request.getName().trim();
        validateNameAvailability(normalizedName, id);
        category.setName(normalizedName);

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new CategoryNotFoundException("Category not found with id: " + id);
        }
        categoryRepository.deleteById(id);
    }

    private void validateNameAvailability(String name, Long excludedCategoryId) {
        if (name == null || name.isBlank()) {
            return;
        }

        Optional<CategoryEntity> category = categoryRepository.findByName(name);
        if (category.isPresent() && (excludedCategoryId == null || !category.get().getId().equals(excludedCategoryId))) {
            throw new CategoryAlreadyExistsException("Category with name '" + name + "' already exists.");
        }
    }

    private CategoryResponseDTO toResponse(CategoryEntity category) {
        CategoryResponseDTO response = new CategoryResponseDTO();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setCreatedAt(category.getCreatedAt());
        return response;
    }
}

