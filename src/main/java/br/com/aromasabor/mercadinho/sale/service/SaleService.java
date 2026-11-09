package br.com.aromasabor.mercadinho.sale.service;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.exception.ProductCostNotDefinedException;
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
import java.util.ArrayList;
import java.util.HashMap;
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

        BigDecimal subtotal = BigDecimal.ZERO;
        Map<Long, Long> quantitiesByProduct = new LinkedHashMap<>();
        List<SaleItemEntity> saleItems = new ArrayList<>();
        List<BigDecimal> itemSubtotals = new ArrayList<>();

        for (SaleItemRequestDTO requestedItem : request.getItems()) {
            ProductEntity product = productsById.get(requestedItem.getProductId());
            if (product == null || !Boolean.TRUE.equals(product.getActive())) {
                throw new ProductNotFoundException("Product not found with id: " + requestedItem.getProductId());
            }

            BigDecimal unitCostPrice = product.getCostPrice();
            if (unitCostPrice == null) {
                throw new ProductCostNotDefinedException(
                        "Produto '" + product.getName() + "' não possui custo cadastrado para a venda.");
            }
            validateUnitPrice(unitCostPrice, false);

            BigDecimal unitPrice = requestedItem.getUnitPrice() != null
                    ? requestedItem.getUnitPrice().setScale(2)
                    : product.getPrice();
            validateUnitPrice(unitPrice, requestedItem.getUnitPrice() != null);
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(requestedItem.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);
            itemSubtotals.add(itemSubtotal);
            quantitiesByProduct.merge(product.getId(), requestedItem.getQuantity().longValue(), Long::sum);

            Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
            String categoryName = product.getCategory() == null ? null : product.getCategory().getName();

            saleItems.add(SaleItemEntity.builder()
                    .product(product)
                    .quantity(requestedItem.getQuantity())
                    .unitPrice(unitPrice)
                    .unitCostPrice(unitCostPrice)
                    .productNameSnapshot(product.getName())
                    .categoryIdSnapshot(categoryId)
                    .categoryNameSnapshot(categoryName)
                    .subtotal(itemSubtotal)
                    .build());
        }

        if (discountAmount.compareTo(subtotal) > 0) {
            throw new InvalidSaleAmountException("O desconto não pode ser maior que o subtotal da venda.");
        }

        quantitiesByProduct.forEach((productId, requestedQuantity) ->
                validateStock(productsById.get(productId), requestedQuantity));
        List<BigDecimal> allocatedDiscounts = SaleDiscountAllocator.allocate(discountAmount, itemSubtotals);
        for (int index = 0; index < saleItems.size(); index++) {
            saleItems.get(index).setDiscountAllocatedAmount(allocatedDiscounts.get(index));
        }

        LocalDateTime now = LocalDateTime.now();
        TurnEntity openTurn = turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)
                .orElseThrow(() -> new MarketClosedException(
                        "Não é possível registrar a venda porque o mercado está fechado."));
        SaleEntity sale = SaleEntity.builder()
                .status(SALE_STATUS_COMPLETED)
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .total(subtotal.subtract(discountAmount))
                .createdAt(now)
                .turn(openTurn)
                .build();
        saleItems.forEach(sale::addItem);

        Map<Long, Integer> stockAfterByProduct = new HashMap<>();
        quantitiesByProduct.keySet().forEach(productId -> stockAfterByProduct.put(
                productId, productsById.get(productId).getStock()));
        List<StockMovementEntity> stockMovements = new ArrayList<>();
        for (SaleItemEntity item : saleItems) {
            ProductEntity product = item.getProduct();
            int stockAfter = stockAfterByProduct.get(product.getId()) - item.getQuantity();
            stockAfterByProduct.put(product.getId(), stockAfter);
            stockMovements.add(StockMovementEntity.builder()
                    .product(product)
                    .movementType(MOVEMENT_TYPE_SALE)
                    .quantity(-item.getQuantity())
                    .stockAfter(stockAfter)
                    .createdAt(now)
                    .build());
        }
        stockAfterByProduct.forEach((productId, stock) -> productsById.get(productId).setStock(stock));

        SaleEntity savedSale = saleRepository.save(sale);

        stockMovements.forEach(movement -> {
            movement.setReferenceSale(savedSale);
            movement.setReason("Venda #" + savedSale.getId());
        });

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
        if (integerDigits(discount) > 8) {
            throw new InvalidSaleAmountException("O desconto deve ter no máximo 8 dígitos inteiros.");
        }
        return discount.setScale(2);
    }

    private void validateNegotiatedUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null) {
            return;
        }
        validateUnitPrice(unitPrice, true);
    }

    private void validateUnitPrice(BigDecimal unitPrice, boolean mustBePositive) {
        if (unitPrice == null
                || (mustBePositive && unitPrice.signum() <= 0)
                || (!mustBePositive && unitPrice.signum() < 0)
                || unitPrice.stripTrailingZeros().scale() > 2
                || integerDigits(unitPrice) > 8) {
            throw new InvalidSaleAmountException(
                    mustBePositive
                            ? "O preço unitário deve ser positivo e ter no máximo 8 dígitos inteiros e 2 decimais."
                            : "O custo unitário deve ser não negativo e ter no máximo 8 dígitos inteiros e 2 decimais.");
        }
    }

    private int integerDigits(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        return Math.max(0, normalized.precision() - normalized.scale());
    }

    private void validateStock(ProductEntity product, long requestedQuantity) {
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
        response.setProductName(item.getProductNameSnapshot());
        response.setCategoryId(item.getCategoryIdSnapshot());
        response.setCategoryName(item.getCategoryNameSnapshot());
        response.setQuantity(item.getQuantity());
        response.setUnitPrice(item.getUnitPrice());
        response.setUnitCostPrice(item.getUnitCostPrice());
        response.setSubtotal(item.getSubtotal());
        response.setDiscountAllocatedAmount(item.getDiscountAllocatedAmount());
        return response;
    }
}
