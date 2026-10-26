package br.com.aromasabor.mercadinho.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.sale.repository.SaleItemRepository;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.TopSellingProductProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.TurnSalesSummaryProjection;
import br.com.aromasabor.mercadinho.turn.dto.TurnSummaryResponse;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.exception.TurnNotFoundException;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TurnSummaryServiceTest {

    @Mock
    private TurnRepository turnRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private TurnService turnService;

    @Test
    void shouldReturnSummaryForClosedTurnWithSales() {
        UUID turnId = UUID.randomUUID();
        TurnEntity turn = buildTurn(turnId, TurnStatus.CLOSED, LocalDateTime.now().minusHours(2), LocalDateTime.now());
        stubTurn(turn);
        when(saleRepository.getSummaryByTurnId(turnId)).thenReturn(new SalesSummary(2L, new BigDecimal("85.05")));
        when(saleItemRepository.findTopSellingProductsByTurnId(eq(turnId), any())).thenReturn(List.of(
                new TopProduct(1L, "Arroz", 5L, new BigDecimal("50.00"))));
        when(productRepository.countByActiveTrueAndStockLessThanEqual(5)).thenReturn(3L);

        TurnSummaryResponse response = turnService.getSummary(turnId);

        assertThat(response.getTurnId()).isEqualTo(turnId);
        assertThat(response.getStatus()).isEqualTo(TurnStatus.CLOSED);
        assertThat(response.getDurationInMinutes()).isEqualTo(120L);
        assertThat(response.getSalesCount()).isEqualTo(2L);
        assertThat(response.getTotalRevenue()).isEqualByComparingTo("85.05");
        assertThat(response.getAverageTicket()).isEqualByComparingTo("42.53");
        assertThat(response.getTopProducts()).singleElement()
                .extracting(product -> product.getProductName())
                .isEqualTo("Arroz");
        assertThat(response.getLowStockProductCount()).isEqualTo(3L);
    }

    @Test
    void shouldReturnCurrentDurationForOpenTurn() {
        UUID turnId = UUID.randomUUID();
        stubTurn(buildTurn(turnId, TurnStatus.OPEN, LocalDateTime.now().minusMinutes(90), null));
        stubEmptyMetrics(turnId);

        TurnSummaryResponse response = turnService.getSummary(turnId);

        assertThat(response.getClosedAt()).isNull();
        assertThat(response.getDurationInMinutes()).isGreaterThanOrEqualTo(90L);
    }

    @Test
    void shouldReturnZeroValuesAndNoProductsWhenTurnHasNoSales() {
        UUID turnId = UUID.randomUUID();
        stubTurn(buildTurn(turnId, TurnStatus.CLOSED, LocalDateTime.now().minusHours(1), LocalDateTime.now()));
        stubEmptyMetrics(turnId);

        TurnSummaryResponse response = turnService.getSummary(turnId);

        assertThat(response.getSalesCount()).isZero();
        assertThat(response.getTotalRevenue()).isEqualByComparingTo("0.00");
        assertThat(response.getAverageTicket()).isEqualByComparingTo("0.00");
        assertThat(response.getTopProducts()).isEmpty();
    }

    @Test
    void shouldKeepFiveTopProductsReturnedByRepository() {
        UUID turnId = UUID.randomUUID();
        stubTurn(buildTurn(turnId, TurnStatus.CLOSED, LocalDateTime.now().minusHours(1), LocalDateTime.now()));
        when(saleRepository.getSummaryByTurnId(turnId)).thenReturn(new SalesSummary(5L, new BigDecimal("100.00")));
        when(saleItemRepository.findTopSellingProductsByTurnId(eq(turnId), any())).thenReturn(List.of(
                new TopProduct(1L, "A", 5L, new BigDecimal("20.00")),
                new TopProduct(2L, "B", 4L, new BigDecimal("20.00")),
                new TopProduct(3L, "C", 3L, new BigDecimal("20.00")),
                new TopProduct(4L, "D", 2L, new BigDecimal("20.00")),
                new TopProduct(5L, "E", 1L, new BigDecimal("20.00"))));
        when(productRepository.countByActiveTrueAndStockLessThanEqual(5)).thenReturn(0L);

        TurnSummaryResponse response = turnService.getSummary(turnId);

        assertThat(response.getTopProducts()).hasSize(5);
    }

    @Test
    void shouldReturnAtMostFiveTopProducts() {
        UUID turnId = UUID.randomUUID();
        stubTurn(buildTurn(turnId, TurnStatus.CLOSED, LocalDateTime.now().minusHours(1), LocalDateTime.now()));
        when(saleRepository.getSummaryByTurnId(turnId)).thenReturn(new SalesSummary(6L, new BigDecimal("120.00")));
        when(saleItemRepository.findTopSellingProductsByTurnId(eq(turnId), any())).thenReturn(List.of(
                new TopProduct(1L, "A", 6L, new BigDecimal("20.00")),
                new TopProduct(2L, "B", 5L, new BigDecimal("20.00")),
                new TopProduct(3L, "C", 4L, new BigDecimal("20.00")),
                new TopProduct(4L, "D", 3L, new BigDecimal("20.00")),
                new TopProduct(5L, "E", 2L, new BigDecimal("20.00")),
                new TopProduct(6L, "F", 1L, new BigDecimal("20.00"))));
        when(productRepository.countByActiveTrueAndStockLessThanEqual(5)).thenReturn(0L);

        TurnSummaryResponse response = turnService.getSummary(turnId);

        assertThat(response.getTopProducts()).hasSize(5);
        assertThat(response.getTopProducts()).extracting(product -> product.getProductName())
                .doesNotContain("F");
    }

    @Test
    void shouldKeepRepositoryRankingOrderForQuantityRevenueAndNameTies() {
        UUID turnId = UUID.randomUUID();
        stubTurn(buildTurn(turnId, TurnStatus.CLOSED, LocalDateTime.now().minusHours(1), LocalDateTime.now()));
        when(saleRepository.getSummaryByTurnId(turnId)).thenReturn(new SalesSummary(4L, new BigDecimal("90.00")));
        when(saleItemRepository.findTopSellingProductsByTurnId(eq(turnId), any())).thenReturn(List.of(
                new TopProduct(1L, "Feijao", 10L, new BigDecimal("30.00")),
                new TopProduct(2L, "Arroz", 10L, new BigDecimal("25.00")),
                new TopProduct(3L, "Biscoito", 8L, new BigDecimal("20.00")),
                new TopProduct(4L, "Acucar", 8L, new BigDecimal("15.00")),
                new TopProduct(5L, "Cafe", 8L, new BigDecimal("15.00"))));
        when(productRepository.countByActiveTrueAndStockLessThanEqual(5)).thenReturn(0L);

        TurnSummaryResponse response = turnService.getSummary(turnId);

        assertThat(response.getTopProducts()).extracting(product -> product.getProductName())
                .containsExactly("Feijao", "Arroz", "Biscoito", "Acucar", "Cafe");
    }

    @Test
    void shouldThrowWhenTurnDoesNotExist() {
        UUID turnId = UUID.randomUUID();
        when(turnRepository.findById(turnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> turnService.getSummary(turnId))
                .isInstanceOf(TurnNotFoundException.class)
                .hasMessage("Turn not found with id: " + turnId);
    }

    private void stubTurn(TurnEntity turn) {
        when(turnRepository.findById(turn.getId())).thenReturn(Optional.of(turn));
    }

    private void stubEmptyMetrics(UUID turnId) {
        when(saleRepository.getSummaryByTurnId(turnId)).thenReturn(new SalesSummary(0L, BigDecimal.ZERO));
        when(saleItemRepository.findTopSellingProductsByTurnId(eq(turnId), any())).thenReturn(List.of());
        when(productRepository.countByActiveTrueAndStockLessThanEqual(5)).thenReturn(0L);
    }

    private TurnEntity buildTurn(UUID id, TurnStatus status, LocalDateTime openedAt, LocalDateTime closedAt) {
        return TurnEntity.builder()
                .id(id)
                .openedAt(openedAt)
                .closedAt(closedAt)
                .operatorName("Maria")
                .status(status)
                .build();
    }

    private record SalesSummary(Long salesCount, BigDecimal totalRevenue) implements TurnSalesSummaryProjection {
        @Override
        public Long getSalesCount() {
            return salesCount;
        }

        @Override
        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }
    }

    private record TopProduct(Long productId, String productName, Long quantitySold, BigDecimal revenue)
            implements TopSellingProductProjection {
        @Override
        public Long getProductId() {
            return productId;
        }

        @Override
        public String getProductName() {
            return productName;
        }

        @Override
        public Long getQuantitySold() {
            return quantitySold;
        }

        @Override
        public BigDecimal getRevenue() {
            return revenue;
        }
    }
}
