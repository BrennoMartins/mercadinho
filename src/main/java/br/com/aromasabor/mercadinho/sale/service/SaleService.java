package br.com.aromasabor.mercadinho.sale.service;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.exception.ProductNotFoundException;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemResponseDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleResponseDTO;
import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.entity.SaleItemEntity;
import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
import br.com.aromasabor.mercadinho.sale.exception.InvalidSaleAmountException;
import br.com.aromasabor.mercadinho.sale.exception.MarketClosedException;
import br.com.aromasabor.mercadinho.sale.exception.ProductOutOfStockException;
import br.com.aromasabor.mercadinho.sale.exception.SaleNotFoundException;
import br.com.aromasabor.mercadinho.sale.exception.SaleWithoutItemsException;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.StockMovementRepository;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

    private static final String SALE_STATUS_COMPLETED = "COMPLETED";
    private static final String MOVEMENT_TYPE_SALE = "SALE";

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TurnRepository turnRepository;

    public SaleService(SaleRepository saleRepository,
                       ProductRepository productRepository,
                       StockMovementRepository stockMovementRepository,
                       TurnRepository turnRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.turnRepository = turnRepository;
    }

    @Transactional
    public SaleResponseDTO create(SaleRequestDTO request) {
        validateRequestHasItems(request);
        BigDecimal discountAmount = resolveDiscountAmount(request);
        request.getItems().forEach(item -> validateNegotiatedUnitPrice(item.getUnitPrice()));

        Set<Long> productIds = request.getItems().stream()
                .map(SaleItemRequestDTO::getProductId)
                .collect(Collectors.toSet());

        Map<Long, ProductEntity> productsById = productRepository.findAllByIdInForUpdate(productIds).stream()
                .collect(Collectors.toMap(ProductEntity::getId, product -> product, (left, right) -> left, LinkedHashMap::new));

        if (productsById.size() != productIds.size()) {
            Long missingProductId = productIds.stream()
                    .filter(productId -> !productsById.containsKey(productId))
                    .findFirst()
                    .orElse(null);
            throw new ProductNotFoundException("Product not found with id: " + missingProductId);
        }

        LocalDateTime now = LocalDateTime.now();
        TurnEntity openTurn = turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)
                .orElseThrow(() -> new MarketClosedException(
                        "Não é possível registrar a venda porque o mercado está fechado."));

        SaleEntity sale = SaleEntity.builder()
                .status(SALE_STATUS_COMPLETED)
                .subtotal(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .createdAt(now)
                .turn(openTurn)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (SaleItemRequestDTO requestedItem : request.getItems()) {
            ProductEntity product = productsById.get(requestedItem.getProductId());
            if (product == null || !Boolean.TRUE.equals(product.getActive())) {
                throw new ProductNotFoundException("Product not found with id: " + requestedItem.getProductId());
            }

            validateStock(product, requestedItem.getQuantity());

            BigDecimal unitPrice = requestedItem.getUnitPrice() != null
                    ? requestedItem.getUnitPrice().setScale(2)
                    : product.getPrice();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(requestedItem.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);

            SaleItemEntity saleItem = SaleItemEntity.builder()
                    .product(product)
                    .quantity(requestedItem.getQuantity())
                    .unitPrice(unitPrice)
                    .unitCostPrice(product.getCostPrice())
                    .subtotal(itemSubtotal)
                    .build();

            sale.addItem(saleItem);
            product.setStock(product.getStock() - requestedItem.getQuantity());
        }

        if (discountAmount.compareTo(subtotal) > 0) {
            throw new InvalidSaleAmountException("O desconto não pode ser maior que o subtotal da venda.");
        }

        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(discountAmount);
        sale.setTotal(subtotal.subtract(discountAmount));
        SaleEntity savedSale = saleRepository.save(sale);

        List<StockMovementEntity> stockMovements = savedSale.getItems().stream()
                .map(item -> StockMovementEntity.builder()
                        .product(item.getProduct())
                        .movementType(MOVEMENT_TYPE_SALE)
                        .quantity(-item.getQuantity())
                        .referenceSale(savedSale)
                        .reason("Venda #" + savedSale.getId())
                        .createdAt(now)
                        .build())
                .toList();

        productRepository.saveAll(productsById.values());
        stockMovementRepository.saveAll(stockMovements);

        return toResponse(savedSale);
    }

    @Transactional(readOnly = true)
    public List<SaleResponseDTO> findAll() {
        return saleRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SaleResponseDTO findById(Long id) {
        SaleEntity sale = saleRepository.findWithItemsById(id)
                .orElseThrow(() -> new SaleNotFoundException("Sale not found with id: " + id));
        return toResponse(sale);
    }

    private void validateRequestHasItems(SaleRequestDTO request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new SaleWithoutItemsException("A sale must contain at least one item.");
        }
    }

    private BigDecimal resolveDiscountAmount(SaleRequestDTO request) {
        BigDecimal discount = request.getDiscountAmount();
        if (discount == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (discount.signum() < 0) {
            throw new InvalidSaleAmountException("O desconto não pode ser negativo.");
        }
        if (discount.stripTrailingZeros().scale() > 2) {
            throw new InvalidSaleAmountException("O desconto deve ter no máximo 2 casas decimais.");
        }
        return discount.setScale(2);
    }

    private void validateNegotiatedUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null) {
            return;
        }
        if (unitPrice.signum() <= 0) {
            throw new InvalidSaleAmountException("O preço unitário deve ser maior que zero.");
        }
        if (unitPrice.stripTrailingZeros().scale() > 2) {
            throw new InvalidSaleAmountException("O preço unitário deve ter no máximo 2 casas decimais.");
        }
    }

    private void validateStock(ProductEntity product, Integer requestedQuantity) {
        if (product.getStock() < requestedQuantity) {
            throw new ProductOutOfStockException(
                    "Insufficient stock for product id " + product.getId()
                            + ". Available: " + product.getStock()
                            + ", requested: " + requestedQuantity);
        }
    }

    private SaleResponseDTO toResponse(SaleEntity sale) {
        SaleResponseDTO response = new SaleResponseDTO();
        response.setId(sale.getId());
        response.setSubtotal(sale.getSubtotal());
        response.setDiscountAmount(sale.getDiscountAmount());
        response.setTotal(sale.getTotal());
        response.setStatus(sale.getStatus());
        response.setCreatedAt(sale.getCreatedAt());
        GrossProfitSummary grossProfit = SaleGrossProfitCalculator.calculate(sale);
        response.setGrossProfitKnownAmount("COMPLETED".equals(sale.getStatus())
                ? grossProfit.knownAmount()
                : null);
        response.setGrossProfitComplete("COMPLETED".equals(sale.getStatus()) && grossProfit.isComplete());
        response.setItemsWithoutHistoricalCost(grossProfit.itemsWithoutHistoricalCost());

        List<SaleItemResponseDTO> itemResponses = sale.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        response.setItems(itemResponses);
        return response;
    }

    private SaleItemResponseDTO toItemResponse(SaleItemEntity item) {
        SaleItemResponseDTO response = new SaleItemResponseDTO();
        response.setProductId(item.getProduct().getId());
        response.setProductName(item.getProduct().getName());
        response.setQuantity(item.getQuantity());
        response.setUnitPrice(item.getUnitPrice());
        response.setSubtotal(item.getSubtotal());
        return response;
    }
}
