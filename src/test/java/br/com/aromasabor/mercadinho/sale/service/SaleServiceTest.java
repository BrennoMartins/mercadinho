package br.com.aromasabor.mercadinho.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.product.entity.CategoryEntity;
import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.exception.ProductCostNotDefinedException;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleResponseDTO;
import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
import br.com.aromasabor.mercadinho.sale.exception.InvalidSaleAmountException;
import br.com.aromasabor.mercadinho.sale.exception.MarketClosedException;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.StockMovementRepository;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private TurnRepository turnRepository;

    @InjectMocks
    private SaleService saleService;

    @Test
    void shouldCreateSaleWhenTurnIsOpen() {
        TurnEntity openTurn = buildOpenTurn();
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 10, "7.25");
        SaleRequestDTO request = new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 2)));

        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(rice));
        when(turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)).thenReturn(Optional.of(openTurn));
        when(saleRepository.save(any(SaleEntity.class))).thenAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(100L);
            return sale;
        });

        SaleResponseDTO response = saleService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getTotal()).isEqualByComparingTo("25.00");
        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("10.50");
        assertThat(response.getGrossProfitComplete()).isTrue();
        assertThat(response.getItemsWithoutHistoricalCost()).isZero();
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    void shouldAssociateSaleWithCurrentOpenTurn() {
        TurnEntity openTurn = buildOpenTurn();
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 10);
        SaleRequestDTO request = new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 1)));

        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(rice));
        when(turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)).thenReturn(Optional.of(openTurn));
        when(saleRepository.save(any(SaleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        saleService.create(request);

        ArgumentCaptor<SaleEntity> saleCaptor = ArgumentCaptor.forClass(SaleEntity.class);
        verify(saleRepository).save(saleCaptor.capture());

        assertThat(saleCaptor.getValue().getTurn()).isNotNull();
        assertThat(saleCaptor.getValue().getTurn().getId()).isEqualTo(openTurn.getId());
    }

    @Test
    void shouldRejectSaleWhenThereIsNoOpenTurn() {
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 10);
        SaleRequestDTO request = new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 1)));

        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(rice));
        when(turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saleService.create(request))
                .isInstanceOf(MarketClosedException.class)
                .hasMessage("Não é possível registrar a venda porque o mercado está fechado.");

        verify(saleRepository, never()).save(any(SaleEntity.class));
    }

    @Test
    void shouldCalculateTotalAndCreateSaleItemsCorrectly() {
        TurnEntity openTurn = buildOpenTurn();
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 10, "8.00");
        ProductEntity beans = buildProduct(2L, "Feijao", "9.00", 10, "4.50");
        SaleRequestDTO request = new SaleRequestDTO(List.of(
                new SaleItemRequestDTO(1L, 2),
                new SaleItemRequestDTO(2L, 3)
        ));

        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(rice, beans));
        when(turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)).thenReturn(Optional.of(openTurn));
        when(saleRepository.save(any(SaleEntity.class))).thenAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(101L);
            return sale;
        });

        SaleResponseDTO response = saleService.create(request);
        ArgumentCaptor<SaleEntity> saleCaptor = ArgumentCaptor.forClass(SaleEntity.class);
        verify(saleRepository).save(saleCaptor.capture());

        assertThat(response.getTotal()).isEqualByComparingTo("52.00");
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getSubtotal()).isEqualByComparingTo("25.00");
        assertThat(response.getItems().get(1).getSubtotal()).isEqualByComparingTo("27.00");
        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("22.50");
        assertThat(response.getGrossProfitComplete()).isTrue();
        assertThat(response.getItemsWithoutHistoricalCost()).isZero();
        assertThat(response.getTotal()).isEqualByComparingTo("52.00");
        assertThat(saleCaptor.getValue().getItems()).extracting(item -> item.getUnitCostPrice())
                .containsExactly(new BigDecimal("8.00"), new BigDecimal("4.50"));
    }

    @Test
    void shouldRejectSaleWithoutHistoricalCostBeforeChangingSaleStockOrTurn() {
        ProductEntity product = buildProduct(9L, "Produto sem custo", "5.00", 10, null);
        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(product));

        assertThatThrownBy(() -> saleService.create(
                new SaleRequestDTO(List.of(new SaleItemRequestDTO(9L, 3)))))
                .isInstanceOf(ProductCostNotDefinedException.class)
                .hasMessageContaining("não possui custo cadastrado");

        assertThat(product.getStock()).isEqualTo(10);
        verify(saleRepository, never()).save(any(SaleEntity.class));
        verify(stockMovementRepository, never()).saveAll(any());
        verify(productRepository, never()).saveAll(any());
        verify(turnRepository, never()).findFirstByStatusForShare(TurnStatus.OPEN);
    }

    @Test
    void shouldGenerateStockMovementsForSaleItems() {
        TurnEntity openTurn = buildOpenTurn();
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 10);
        SaleRequestDTO request = new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 2)));

        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(rice));
        when(turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)).thenReturn(Optional.of(openTurn));
        when(saleRepository.save(any(SaleEntity.class))).thenAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(102L);
            return sale;
        });

        saleService.create(request);

        ArgumentCaptor<List<StockMovementEntity>> movementsCaptor = ArgumentCaptor.forClass(List.class);
        verify(stockMovementRepository).saveAll(movementsCaptor.capture());

        List<StockMovementEntity> movements = movementsCaptor.getValue();
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getMovementType()).isEqualTo("SALE");
        assertThat(movements.get(0).getQuantity()).isEqualTo(-2);
        assertThat(movements.get(0).getStockAfter()).isEqualTo(8);
        assertThat(movements.get(0).getReferenceSale().getId()).isEqualTo(102L);
        assertThat(rice.getStock()).isEqualTo(8);
    }

    @Test
    void shouldRecordSequentialBalancesForRepeatedProductItemsInSale() {
        TurnEntity openTurn = buildOpenTurn();
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 10);
        SaleRequestDTO request = new SaleRequestDTO(List.of(
                new SaleItemRequestDTO(1L, 2),
                new SaleItemRequestDTO(1L, 3)));

        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(rice));
        when(turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)).thenReturn(Optional.of(openTurn));
        when(saleRepository.save(any(SaleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        saleService.create(request);

        ArgumentCaptor<List<StockMovementEntity>> movementsCaptor = ArgumentCaptor.forClass(List.class);
        verify(stockMovementRepository).saveAll(movementsCaptor.capture());

        assertThat(movementsCaptor.getValue())
                .extracting(StockMovementEntity::getQuantity)
                .containsExactly(-2, -3);
        assertThat(movementsCaptor.getValue())
                .extracting(StockMovementEntity::getStockAfter)
                .containsExactly(8, 5);
        assertThat(rice.getStock()).isEqualTo(5);
    }

    @Test
    void shouldApplyGeneralDiscountAndNegotiatedPrice() {
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 10, "7.00");
        stubOpenSale(rice);

        SaleResponseDTO response = saleService.create(new SaleRequestDTO(
                List.of(new SaleItemRequestDTO(1L, 2, new BigDecimal("15.00"))), new BigDecimal("5.00")));

        assertThat(response.getSubtotal()).isEqualByComparingTo("30.00");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(response.getTotal()).isEqualByComparingTo("25.00");
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("15.00");
        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("11.00");
        assertThat(rice.getPrice()).isEqualByComparingTo("12.50");
    }

    @Test
    void shouldCaptureHistoricalProductCategoryCostAndAllocatedDiscount() {
        CategoryEntity category = CategoryEntity.builder()
                .id(25L)
                .name("Mercearia histórica")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        ProductEntity rice = buildProduct(1L, "Arroz histórico", "12.50", 10, "7.00");
        rice.setCategory(category);
        stubOpenSale(rice);

        SaleResponseDTO response = saleService.create(new SaleRequestDTO(
                List.of(new SaleItemRequestDTO(1L, 2, new BigDecimal("15.00"))),
                new BigDecimal("5.00")));

        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("Arroz histórico");
            assertThat(item.getCategoryId()).isEqualTo(25L);
            assertThat(item.getCategoryName()).isEqualTo("Mercearia histórica");
            assertThat(item.getUnitCostPrice()).isEqualByComparingTo("7.00");
            assertThat(item.getSubtotal()).isEqualByComparingTo("30.00");
            assertThat(item.getDiscountAllocatedAmount()).isEqualByComparingTo("5.00");
        });
        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("11.00");
    }

    @Test
    void shouldPersistZeroAsKnownHistoricalCost() {
        stubOpenSale(buildProduct(1L, "Produto sem custo de aquisição", "5.00", 10, "0.00"));

        SaleResponseDTO response = saleService.create(
                new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 2))));

        assertThat(response.getGrossProfitComplete()).isTrue();
        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldKeepTotalsAndItemProfitsConsistentAfterProportionalDiscountAllocation() {
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 10, "8.00");
        ProductEntity beans = buildProduct(2L, "Feijao", "9.00", 10, "4.50");
        stubOpenSale(rice, beans);

        SaleResponseDTO response = saleService.create(new SaleRequestDTO(List.of(
                new SaleItemRequestDTO(1L, 2),
                new SaleItemRequestDTO(2L, 3)), new BigDecimal("3.00")));

        BigDecimal allocatedDiscount = response.getItems().stream()
                .map(item -> item.getDiscountAllocatedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemGrossProfit = response.getItems().stream()
                .map(item -> item.getSubtotal()
                        .subtract(item.getDiscountAllocatedAmount())
                        .subtract(item.getUnitCostPrice().multiply(BigDecimal.valueOf(item.getQuantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = response.getItems().stream()
                .map(item -> item.getUnitCostPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(response.getSubtotal()).isEqualByComparingTo("52.00");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("3.00");
        assertThat(response.getTotal()).isEqualByComparingTo("49.00");
        assertThat(allocatedDiscount).isEqualByComparingTo(response.getDiscountAmount());
        assertThat(totalCost).isEqualByComparingTo("29.50");
        assertThat(itemGrossProfit).isEqualByComparingTo("19.50");
        assertThat(itemGrossProfit).isEqualByComparingTo(response.getGrossProfitKnownAmount());
    }

    @Test
    void shouldRejectRepeatedProductQuantityAboveStockWithoutMutatingProduct() {
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 4, "7.00");
        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(rice));

        assertThatThrownBy(() -> saleService.create(new SaleRequestDTO(List.of(
                new SaleItemRequestDTO(1L, 3),
                new SaleItemRequestDTO(1L, 2)))))
                .isInstanceOf(br.com.aromasabor.mercadinho.sale.exception.ProductOutOfStockException.class);

        assertThat(rice.getStock()).isEqualTo(4);
        verify(saleRepository, never()).save(any(SaleEntity.class));
        verify(stockMovementRepository, never()).saveAll(any());
    }

    @Test
    void shouldDefaultDiscountToZeroWhenOmitted() {
        stubOpenSale(buildProduct(1L, "Arroz", "12.50", 10, "7.00"));

        SaleResponseDTO response = saleService.create(new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 2))));

        assertThat(response.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(response.getSubtotal()).isEqualByComparingTo("25.00");
        assertThat(response.getTotal()).isEqualByComparingTo("25.00");
    }

    @Test
    void shouldAllowDiscountEqualToSubtotal() {
        stubOpenSale(buildProduct(1L, "Arroz", "12.50", 10, "7.00"));

        SaleResponseDTO response = saleService.create(new SaleRequestDTO(
                List.of(new SaleItemRequestDTO(1L, 2)), new BigDecimal("25.00")));

        assertThat(response.getTotal()).isEqualByComparingTo("0.00");
        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("-14.00");
    }

    @Test
    void shouldRejectInvalidDiscountsAndPricesBeforeTouchingData() {
        List<SaleRequestDTO> invalidRequests = List.of(
                new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 1)), new BigDecimal("-0.01")),
                new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 1)), new BigDecimal("1.001")),
                new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 1, BigDecimal.ZERO))),
                new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 1, new BigDecimal("-1.00")))),
                new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 1, new BigDecimal("1.001")))));

        invalidRequests.forEach(request -> assertThatThrownBy(() -> saleService.create(request))
                .isInstanceOf(InvalidSaleAmountException.class));

        verify(productRepository, never()).findAllByIdInForUpdate(any());
        verify(saleRepository, never()).save(any(SaleEntity.class));
    }

    @Test
    void shouldRejectDiscountAboveSubtotalWithoutChangingStock() {
        ProductEntity rice = buildProduct(1L, "Arroz", "12.50", 10, "7.00");
        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(rice));

        assertThatThrownBy(() -> saleService.create(new SaleRequestDTO(
                List.of(new SaleItemRequestDTO(1L, 1)), new BigDecimal("12.51"))))
                .isInstanceOf(InvalidSaleAmountException.class);

        verify(saleRepository, never()).save(any(SaleEntity.class));
        verify(stockMovementRepository, never()).saveAll(any());
    }

    private void stubOpenSale(ProductEntity... products) {
        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(products));
        when(turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)).thenReturn(Optional.of(buildOpenTurn()));
        when(saleRepository.save(any(SaleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TurnEntity buildOpenTurn() {
        return TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(LocalDateTime.now().minusHours(1))
                .operatorName("Maria")
                .status(TurnStatus.OPEN)
                .openingNote("Inicio")
                .build();
    }

    private ProductEntity buildProduct(Long id, String name, String price, Integer stock) {
        return buildProduct(id, name, price, stock, "6.00");
    }

    private ProductEntity buildProduct(Long id, String name, String price, Integer stock, String costPrice) {
        return ProductEntity.builder()
                .id(id)
                .barcode("789" + id)
                .name(name)
                .price(new BigDecimal(price))
                .costPrice(costPrice == null ? null : new BigDecimal(costPrice))
                .stock(stock)
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }
}
