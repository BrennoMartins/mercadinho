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
    void shouldKeepIncompleteIndicatorAndAllowNegativeKnownProfit() {
        SaleEntity sale = sale("COMPLETED", "5.00");
        sale.addItem(item("10.00", "9.00", 1));
        sale.addItem(item("10.00", null, 1));

        GrossProfitSummary summary = SaleGrossProfitCalculator.calculate(sale);

        assertThat(summary.knownAmount()).isEqualByComparingTo("-4.00");
        assertThat(summary.itemsWithoutHistoricalCost()).isEqualTo(1);
        assertThat(summary.isComplete()).isFalse();
    }

    @Test
    void shouldIgnoreDiscountOfNonCompletedSales() {
        SaleEntity sale = sale("CANCELLED", "5.00");
        sale.addItem(item("10.00", "6.00", 1));

        assertThat(SaleGrossProfitCalculator.calculate(sale).knownAmount()).isEqualByComparingTo("0.00");
    }

    private SaleEntity sale(String status, String discount) {
        return SaleEntity.builder().status(status).discountAmount(new BigDecimal(discount)).build();
    }

    private SaleItemEntity item(String price, String cost, int quantity) {
        return SaleItemEntity.builder()
                .unitPrice(new BigDecimal(price))
                .unitCostPrice(cost == null ? null : new BigDecimal(cost))
                .quantity(quantity)
                .build();
    }
}
