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
        BigDecimal knownItemsAllocatedDiscount = ZERO_MONEY;
        long itemsWithoutHistoricalCost = 0;
        BigDecimal discount = sale.getDiscountAmount() == null ? ZERO_MONEY : sale.getDiscountAmount();
        boolean hasCompleteItemDiscountAllocations = !sale.getItems().isEmpty()
                && sale.getItems().stream().allMatch(item -> item.getDiscountAllocatedAmount() != null);
        boolean hasKnownItemWithoutDiscountAllocation = sale.getItems().stream()
                .anyMatch(item -> item.getUnitCostPrice() != null && item.getDiscountAllocatedAmount() == null);

        for (SaleItemEntity item : sale.getItems()) {
            BigDecimal unitCost = item.getUnitCostPrice();
            if (unitCost == null) {
                itemsWithoutHistoricalCost++;
                continue;
            }

            BigDecimal unitGrossProfit = item.getUnitPrice().subtract(unitCost);
            knownAmount = knownAmount.add(unitGrossProfit.multiply(BigDecimal.valueOf(item.getQuantity())));
            if (discount.signum() > 0 && item.getDiscountAllocatedAmount() != null) {
                knownItemsAllocatedDiscount = knownItemsAllocatedDiscount.add(item.getDiscountAllocatedAmount());
            }
        }

        if (itemsWithoutHistoricalCost > 0) {
            if (discount.signum() > 0 && hasKnownItemWithoutDiscountAllocation) {
                return new GrossProfitSummary(null, itemsWithoutHistoricalCost);
            }
            knownAmount = knownAmount.subtract(knownItemsAllocatedDiscount);
        } else if (hasCompleteItemDiscountAllocations && discount.signum() > 0) {
            knownAmount = knownAmount.subtract(knownItemsAllocatedDiscount);
        } else if (sale.getItems().stream().anyMatch(item -> item.getDiscountAllocatedAmount() == null)) {
            knownAmount = knownAmount.subtract(discount);
        }

        return new GrossProfitSummary(knownAmount, itemsWithoutHistoricalCost);
    }
}
