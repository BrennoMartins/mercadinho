package br.com.aromasabor.mercadinho.sale.service;

import br.com.aromasabor.mercadinho.sale.dto.StockMovementResponseDTO;
import br.com.aromasabor.mercadinho.sale.exception.InvalidStockMovementFilterException;
import br.com.aromasabor.mercadinho.sale.repository.StockMovementRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockMovementService {

    private static final Set<String> MOVEMENT_TYPES = Set.of("SALE", "ENTRY", "ADJUSTMENT", "DISCARD");
    private static final int MAX_PAGE_SIZE = 100;

    private final StockMovementRepository stockMovementRepository;

    public StockMovementService(StockMovementRepository stockMovementRepository) {
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponseDTO> findHistory(Long productId,
                                                       String movementType,
                                                       LocalDateTime startDate,
                                                       LocalDateTime endDate,
                                                       int page,
                                                       int size) {
        if (productId != null && productId < 1) {
            throw new InvalidStockMovementFilterException("productId deve ser maior que zero.");
        }

        String normalizedMovementType = movementType == null ? null : movementType.trim().toUpperCase(Locale.ROOT);
        if (normalizedMovementType != null
                && (normalizedMovementType.isEmpty() || !MOVEMENT_TYPES.contains(normalizedMovementType))) {
            throw new InvalidStockMovementFilterException(
                    "movementType deve ser SALE, ENTRY, ADJUSTMENT ou DISCARD.");
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidStockMovementFilterException("startDate não pode ser posterior a endDate.");
        }

        if (page < 0) {
            throw new InvalidStockMovementFilterException("page não pode ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidStockMovementFilterException(
                    "size deve estar entre 1 e " + MAX_PAGE_SIZE + ".");
        }

        return stockMovementRepository.findHistory(
                productId,
                normalizedMovementType,
                startDate,
                endDate,
                PageRequest.of(page, size));
    }
}
