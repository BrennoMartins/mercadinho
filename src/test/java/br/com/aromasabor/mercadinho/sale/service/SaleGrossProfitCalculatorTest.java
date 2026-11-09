package br.com.aromasabor.mercadinho.sale.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.entity.SaleItemEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SaleGrossProfitCalculatorTest {

    @Test
    void shouldDeductGeneralDiscountOnceAcrossMultipleItems() {
        SaleEntity sale = sale("COMPLETED", "4.00");
        sale.addItem(item("11.00", "6.00", 1));
        sale.addItem(item("10.00", "6.00", 2));

        GrossProfitSummary summary = SaleGrossProfitCalculator.calculate(sale);

        assertThat(summary.knownAmount()).isEqualByComparingTo("9.00");
        assertThat(summary.isComplete()).isTrue();
    }

    @Test
    void shouldReturnIndeterminateProfitWhenKnownCostItemHasNoAllocationAndDiscountIsPositive() {
        SaleEntity sale = sale("COMPLETED", "3.00");
        sale.addItem(item("10.00", "6.00", 1));
        sale.addItem(item("20.00", null, 1));

        GrossProfitSummary summary = SaleGrossProfitCalculator.calculate(sale);

        assertThat(summary.knownAmount()).isNull();
        assertThat(summary.itemsWithoutHistoricalCost()).isEqualTo(1);
        assertThat(summary.isComplete()).isFalse();
    }

    @Test
    void shouldCalculatePartialProfitWhenUnknownCostItemHasNoAllocation() {
        SaleEntity sale = sale("COMPLETED", "3.00");
        sale.addItem(item("10.00", "6.00", 1, "1.00"));
        sale.addItem(item("20.00", null, 1));

        GrossProfitSummary summary = SaleGrossProfitCalculator.calculate(sale);

        assertThat(summary.knownAmount()).isEqualByComparingTo("3.00");
        assertThat(summary.itemsWithoutHistoricalCost()).isEqualTo(1);
        assertThat(summary.isComplete()).isFalse();
    }

    @Test
    void shouldCalculatePartialProfitWithoutDiscountWhenHistoricalCostIsMissing() {
        SaleEntity sale = sale("COMPLETED", "0.00");
        sale.addItem(item("10.00", "6.00", 1));
        sale.addItem(item("20.00", null, 1));

        GrossProfitSummary summary = SaleGrossProfitCalculator.calculate(sale);

        assertThat(summary.knownAmount()).isEqualByComparingTo("4.00");
        assertThat(summary.itemsWithoutHistoricalCost()).isEqualTo(1);
        assertThat(summary.isComplete()).isFalse();
    }

    @Test
    void shouldIgnoreDiscountOfNonCompletedSales() {
        SaleEntity sale = sale("CANCELLED", "5.00");
        sale.addItem(item("10.00", "6.00", 1));

        assertThat(SaleGrossProfitCalculator.calculate(sale).knownAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldCalculateProfitFromHistoricalItemDiscountAllocations() {
        SaleEntity sale = sale("COMPLETED", "3.00");
        sale.addItem(item("10.00", "6.00", 1, "1.00"));
        sale.addItem(item("20.00", "4.00", 1, "2.00"));

        GrossProfitSummary summary = SaleGrossProfitCalculator.calculate(sale);

        assertThat(summary.knownAmount()).isEqualByComparingTo("17.00");
        assertThat(summary.isComplete()).isTrue();
    }

    @Test
    void shouldSubtractOnlyKnownItemsAllocatedDiscountWhenCostIsIncomplete() {
        SaleEntity sale = sale("COMPLETED", "3.00");
        sale.addItem(item("10.00", "6.00", 1, "1.00"));
        sale.addItem(item("20.00", null, 1, "2.00"));

        GrossProfitSummary summary = SaleGrossProfitCalculator.calculate(sale);

        assertThat(summary.knownAmount()).isEqualByComparingTo("3.00");
        assertThat(summary.itemsWithoutHistoricalCost()).isEqualTo(1);
        assertThat(summary.isComplete()).isFalse();
    }

    @Test
    void shouldUseHeaderDiscountForCompleteProfitWhenAllocationsAreMissing() {
        SaleEntity sale = sale("COMPLETED", "3.00");
        sale.addItem(item("10.00", "6.00", 1));
        sale.addItem(item("20.00", "4.00", 1));

        GrossProfitSummary summary = SaleGrossProfitCalculator.calculate(sale);

        assertThat(summary.knownAmount()).isEqualByComparingTo("17.00");
        assertThat(summary.itemsWithoutHistoricalCost()).isZero();
        assertThat(summary.isComplete()).isTrue();
    }

    @Test
    void shouldNotDeductKnownAllocationsAgainWhenCompleteCostsUseHeaderDiscountFallback() {
        SaleEntity sale = sale("COMPLETED", "3.00");
        sale.addItem(item("10.00", "6.00", 1, "1.00"));
        sale.addItem(item("20.00", "4.00", 1));

        GrossProfitSummary summary = SaleGrossProfitCalculator.calculate(sale);

        assertThat(summary.knownAmount()).isEqualByComparingTo("17.00");
        assertThat(summary.isComplete()).isTrue();
    }

    private SaleEntity sale(String status, String discount) {
        return SaleEntity.builder().status(status).discountAmount(new BigDecimal(discount)).build();
    }

    private SaleItemEntity item(String price, String cost, int quantity) {
        return item(price, cost, quantity, null);
    }

    private SaleItemEntity item(String price, String cost, int quantity, String discountAllocatedAmount) {
        return SaleItemEntity.builder()
                .unitPrice(new BigDecimal(price))
                .unitCostPrice(cost == null ? null : new BigDecimal(cost))
                .quantity(quantity)
                .discountAllocatedAmount(discountAllocatedAmount == null
                        ? null
                        : new BigDecimal(discountAllocatedAmount))
                .build();
    }
}
