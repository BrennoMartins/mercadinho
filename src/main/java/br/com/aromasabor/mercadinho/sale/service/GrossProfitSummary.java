package br.com.aromasabor.mercadinho.sale.service;

import java.math.BigDecimal;

public record GrossProfitSummary(BigDecimal knownAmount, long itemsWithoutHistoricalCost) {

    public boolean isComplete() {
        return itemsWithoutHistoricalCost == 0;
    }
}
