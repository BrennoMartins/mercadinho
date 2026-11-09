package br.com.aromasabor.mercadinho.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaleDiscountAllocatorTest {

    @Test
    void shouldAllocateDiscountProportionallyAcrossDifferentItemSubtotals() {
        List<BigDecimal> allocations = SaleDiscountAllocator.allocate(
                money("3.00"), List.of(money("10.00"), money("20.00")));

        assertThat(allocations).containsExactly(money("1.00"), money("2.00"));
    }

    @Test
    void shouldReturnZeroAllocationsWhenDiscountIsZeroOrAllSubtotalsAreZero() {
        assertThat(SaleDiscountAllocator.allocate(
                money("0.00"), List.of(money("1.00"), money("2.00"))))
                .containsExactly(money("0.00"), money("0.00"));
        assertThat(SaleDiscountAllocator.allocate(
                money("0.00"), List.of(money("0.00"), money("0.00"))))
                .containsExactly(money("0.00"), money("0.00"));
    }

    @Test
    void shouldAllocateFullSubtotalWhenDiscountEqualsSaleSubtotal() {
        assertThat(SaleDiscountAllocator.allocate(
                money("3.01"), List.of(money("1.01"), money("2.00"))))
                .containsExactly(money("1.01"), money("2.00"));
    }

    @Test
    void shouldDistributeResidualCentsByLargestRemainder() {
        List<BigDecimal> allocations = SaleDiscountAllocator.allocate(
                money("0.02"), List.of(money("1.00"), money("1.00"), money("1.00")));

        assertThat(allocations).containsExactly(money("0.01"), money("0.01"), money("0.00"));
    }

    @Test
    void shouldBreakEqualRemaindersByOriginalItemPosition() {
        List<BigDecimal> allocations = SaleDiscountAllocator.allocate(
                money("0.01"), List.of(money("1.00"), money("1.00"), money("1.00")));

        assertThat(allocations).containsExactly(money("0.01"), money("0.00"), money("0.00"));
    }

    @Test
    void shouldPreserveDiscountAndNetTotalForUnevenCentSharesWithoutOverDiscountingItems() {
        List<BigDecimal> subtotals = List.of(money("0.01"), money("0.02"), money("0.05"), money("1.00"));
        BigDecimal discount = money("0.07");
        List<BigDecimal> allocations = SaleDiscountAllocator.allocate(discount, subtotals);

        BigDecimal allocatedTotal = allocations.stream().reduce(money("0.00"), BigDecimal::add);
        BigDecimal netTotal = subtotals.stream().reduce(money("0.00"), BigDecimal::add)
                .subtract(allocatedTotal);

        assertThat(allocatedTotal).isEqualByComparingTo(discount);
        assertThat(netTotal).isEqualByComparingTo("1.01");
        for (int index = 0; index < subtotals.size(); index++) {
            assertThat(allocations.get(index)).isLessThanOrEqualTo(subtotals.get(index));
        }
    }

    @Test
    void shouldRejectDiscountGreaterThanSubtotalOrPositiveDiscountOnZeroSubtotal() {
        assertThatThrownBy(() -> SaleDiscountAllocator.allocate(
                money("2.01"), List.of(money("1.00"), money("1.00"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaleDiscountAllocator.allocate(
                money("0.01"), List.of(money("0.00"), money("0.00"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
