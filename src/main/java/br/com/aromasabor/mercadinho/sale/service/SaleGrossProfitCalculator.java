package br.com.aromasabor.mercadinho.sale.service;

import br.com.aromasabor.mercadinho.sale.entity.SaleEntity;
import br.com.aromasabor.mercadinho.sale.entity.SaleItemEntity;
import java.math.BigDecimal;

public final class SaleGrossProfitCalculator {

    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private SaleGrossProfitCalculator() {
    }

    public static GrossProfitSummary calculate(SaleEntity sale) {
        if (!COMPLETED_STATUS.equals(sale.getStatus())) {
            return new GrossProfitSummary(ZERO_MONEY, 0);
        }

        BigDecimal knownAmount = ZERO_MONEY;
        long itemsWithoutHistoricalCost = 0;

        for (SaleItemEntity item : sale.getItems()) {
            BigDecimal unitCost = item.getUnitCostPrice();
            if (unitCost == null) {
                itemsWithoutHistoricalCost++;
                continue;
            }

            BigDecimal unitGrossProfit = item.getUnitPrice().subtract(unitCost);
            knownAmount = knownAmount.add(unitGrossProfit.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        BigDecimal discount = sale.getDiscountAmount() == null ? ZERO_MONEY : sale.getDiscountAmount();
        // The general discount is deducted once per sale; it may make the known profit negative.
        return new GrossProfitSummary(knownAmount.subtract(discount), itemsWithoutHistoricalCost);
    }
}
