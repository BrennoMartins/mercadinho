package br.com.aromasabor.mercadinho.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.product.entity.ProductEntity;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleResponseDTO;
import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.entity.StockMovementEntity;
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
    void shouldReportKnownGrossProfitAsIncompleteWhenProductHasNoCost() {
        TurnEntity openTurn = buildOpenTurn();
        ProductEntity product = buildProduct(9L, "Produto sem custo", "5.00", 10);
        when(productRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(product));
        when(turnRepository.findFirstByStatusForShare(TurnStatus.OPEN)).thenReturn(Optional.of(openTurn));
        when(saleRepository.save(any(SaleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SaleResponseDTO response = saleService.create(
                new SaleRequestDTO(List.of(new SaleItemRequestDTO(9L, 3))));

        assertThat(response.getGrossProfitKnownAmount()).isEqualByComparingTo("0.00");
        assertThat(response.getGrossProfitComplete()).isFalse();
        assertThat(response.getItemsWithoutHistoricalCost()).isEqualTo(1L);
        assertThat(response.getTotal()).isEqualByComparingTo("15.00");
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
        assertThat(movements.get(0).getReferenceSale().getId()).isEqualTo(102L);
        assertThat(rice.getStock()).isEqualTo(8);
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
        return buildProduct(id, name, price, stock, null);
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
