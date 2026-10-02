package br.com.aromasabor.mercadinho.turn.service;

import br.com.aromasabor.mercadinho.turn.dto.CloseTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.CloseTurnResponse;
import br.com.aromasabor.mercadinho.turn.dto.OpenTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.TopSellingProductResponse;
import br.com.aromasabor.mercadinho.turn.dto.TurnResponse;
import br.com.aromasabor.mercadinho.turn.dto.TurnSummaryResponse;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.repository.SaleItemRepository;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.TopSellingProductProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.TurnSalesSummaryProjection;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.exception.NoOpenTurnException;
import br.com.aromasabor.mercadinho.turn.exception.TurnAlreadyOpenException;
import br.com.aromasabor.mercadinho.turn.exception.TurnNotFoundException;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TurnService {

    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final int TOP_PRODUCTS_LIMIT = 5;
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final TurnRepository turnRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;

    public TurnService(TurnRepository turnRepository,
                       SaleRepository saleRepository,
                       SaleItemRepository saleItemRepository,
                       ProductRepository productRepository) {
        this.turnRepository = turnRepository;
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public TurnResponse open(OpenTurnRequest request) {
        if (turnRepository.existsByStatus(TurnStatus.OPEN)) {
            throw new TurnAlreadyOpenException("There is already an open turn.");
        }

        LocalDateTime now = LocalDateTime.now();
        TurnEntity turn = TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(now)
                .operatorName(request.getOperatorName())
                .status(TurnStatus.OPEN)
                .openingNote(request.getOpeningNote())
                .build();

        try {
            return toTurnResponse(turnRepository.save(turn));
        } catch (DataIntegrityViolationException ex) {
            throw new TurnAlreadyOpenException("There is already an open turn.");
        }
    }

    @Transactional(readOnly = true)
    public TurnResponse findCurrentOpen() {
        TurnEntity turn = turnRepository.findFirstByStatusOrderByOpenedAtDesc(TurnStatus.OPEN)
                .orElseThrow(() -> new NoOpenTurnException("No open turn found."));
        return toTurnResponse(turn);
    }

    @Transactional
    public CloseTurnResponse close(UUID id, CloseTurnRequest request) {
        TurnEntity turn = turnRepository.findByIdAndStatus(id, TurnStatus.OPEN)
                .orElseThrow(() -> new NoOpenTurnException("No open turn found with id: " + id));

        LocalDateTime closedAt = LocalDateTime.now();
        turn.setClosedAt(closedAt);
        turn.setStatus(TurnStatus.CLOSED);
        turn.setClosingNote(request.getClosingNote());

        TurnEntity savedTurn = turnRepository.save(turn);
        return toCloseTurnResponse(savedTurn);
    }

    @Transactional(readOnly = true)
    public List<TurnResponse> findAll() {
        return turnRepository.findAllByOrderByOpenedAtDesc().stream()
                .map(this::toTurnResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TurnSummaryResponse getSummary(UUID turnId) {
        TurnEntity turn = turnRepository.findById(turnId)
                .orElseThrow(() -> new TurnNotFoundException("Turn not found with id: " + turnId));

        TurnSalesSummaryProjection salesSummary = saleRepository.getSummaryByTurnId(turnId);
        long salesCount = salesSummary == null || salesSummary.getSalesCount() == null
                ? 0L
                : salesSummary.getSalesCount();
        BigDecimal totalRevenue = salesSummary == null || salesSummary.getTotalRevenue() == null
                ? ZERO_MONEY
                : salesSummary.getTotalRevenue();

        List<TopSellingProductResponse> topProducts = saleItemRepository
                .findTopSellingProductsByTurnId(turnId, PageRequest.of(0, TOP_PRODUCTS_LIMIT))
                .stream()
                .limit(TOP_PRODUCTS_LIMIT)
                .map(this::toTopSellingProductResponse)
                .toList();

        TurnSummaryResponse response = new TurnSummaryResponse();
        response.setTurnId(turn.getId());
        response.setStatus(turn.getStatus());
        response.setOpenedAt(turn.getOpenedAt());
        response.setClosedAt(turn.getClosedAt());
        response.setDurationInMinutes(calculateDurationInMinutes(
                turn.getOpenedAt(),
                turn.getClosedAt() == null ? LocalDateTime.now() : turn.getClosedAt()));
        response.setSalesCount(salesCount);
        response.setTotalRevenue(totalRevenue);
        response.setAverageTicket(calculateAverageTicket(totalRevenue, salesCount));
        response.setTopProducts(topProducts);
        response.setLowStockProductCount(productRepository
                .countByActiveTrueAndStockLessThanEqual(LOW_STOCK_THRESHOLD));
        return response;
    }

    private TurnResponse toTurnResponse(TurnEntity turn) {
        TurnResponse response = new TurnResponse();
        response.setId(turn.getId());
        response.setOpenedAt(turn.getOpenedAt());
        response.setClosedAt(turn.getClosedAt());
        response.setOperatorName(turn.getOperatorName());
        response.setStatus(turn.getStatus());
        response.setOpeningNote(turn.getOpeningNote());
        response.setClosingNote(turn.getClosingNote());
        response.setDurationInMinutes(calculateDurationInMinutes(turn.getOpenedAt(), turn.getClosedAt()));
        return response;
    }

    private CloseTurnResponse toCloseTurnResponse(TurnEntity turn) {
        CloseTurnResponse response = new CloseTurnResponse();
        response.setId(turn.getId());
        response.setOpenedAt(turn.getOpenedAt());
        response.setClosedAt(turn.getClosedAt());
        response.setStatus(turn.getStatus());
        response.setClosingNote(turn.getClosingNote());
        response.setDurationInMinutes(calculateDurationInMinutes(turn.getOpenedAt(), turn.getClosedAt()));
        return response;
    }

    private TopSellingProductResponse toTopSellingProductResponse(TopSellingProductProjection product) {
        return new TopSellingProductResponse(
                product.getProductId(),
                product.getProductName(),
                product.getQuantitySold(),
                product.getRevenue());
    }

    private BigDecimal calculateAverageTicket(BigDecimal totalRevenue, long salesCount) {
        if (salesCount == 0) {
            return ZERO_MONEY;
        }
        return totalRevenue.divide(BigDecimal.valueOf(salesCount), 2, RoundingMode.HALF_UP);
    }

    private Long calculateDurationInMinutes(LocalDateTime openedAt, LocalDateTime closedAt) {
        if (openedAt == null || closedAt == null) {
            return null;
        }
        return Duration.between(openedAt, closedAt).toMinutes();
    }
}
