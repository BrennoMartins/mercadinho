package br.com.aromasabor.mercadinho.dashboard.service;

import br.com.aromasabor.mercadinho.dashboard.dto.CurrentTurnResponse;
import br.com.aromasabor.mercadinho.dashboard.dto.DashboardSummaryResponse;
import br.com.aromasabor.mercadinho.dashboard.dto.RecentSaleResponse;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesSummaryProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.GrossProfitSummaryProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.RecentSaleProjection;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int RECENT_SALES_LIMIT = 5;
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final SaleRepository saleRepository;
    private final TurnRepository turnRepository;

    public DashboardService(SaleRepository saleRepository, TurnRepository turnRepository) {
        this.saleRepository = saleRepository;
        this.turnRepository = turnRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        LocalDate date = LocalDate.now(ZoneId.systemDefault());
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        DailySalesSummaryProjection salesSummary = saleRepository.getDailySummary(start, end);
        long salesCount = salesSummary == null || salesSummary.getSalesCount() == null
                ? 0L
                : salesSummary.getSalesCount();
        BigDecimal revenue = salesSummary == null || salesSummary.getTotalRevenue() == null
                ? ZERO_MONEY
                : salesSummary.getTotalRevenue();
        GrossProfitSummaryProjection grossProfit = saleRepository.getGrossProfitSummary(start, end);
        BigDecimal grossProfitKnownAmount = grossProfit == null || grossProfit.getGrossProfitKnownAmount() == null
                ? ZERO_MONEY
                : grossProfit.getGrossProfitKnownAmount();
        long itemsWithoutHistoricalCost = grossProfit == null || grossProfit.getItemsWithoutHistoricalCost() == null
                ? 0L
                : grossProfit.getItemsWithoutHistoricalCost();

        List<RecentSaleResponse> recentSales = saleRepository
                .findRecentSales(start, end, PageRequest.of(0, RECENT_SALES_LIMIT))
                .stream()
                .map(this::toRecentSaleResponse)
                .toList();

        CurrentTurnResponse currentTurn = turnRepository
                .findFirstByStatusOrderByOpenedAtDesc(TurnStatus.OPEN)
                .map(this::toCurrentTurnResponse)
                .orElse(null);

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setDate(date);
        response.setRevenue(revenue);
        response.setSalesCount(salesCount);
        response.setAverageTicket(calculateAverageTicket(revenue, salesCount));
        response.setGrossProfitKnownAmount(grossProfitKnownAmount);
        response.setGrossProfitComplete(itemsWithoutHistoricalCost == 0);
        response.setItemsWithoutHistoricalCost(itemsWithoutHistoricalCost);
        response.setCurrentTurn(currentTurn);
        response.setRecentSales(recentSales);
        return response;
    }

    private BigDecimal calculateAverageTicket(BigDecimal revenue, long salesCount) {
        if (salesCount == 0) {
            return ZERO_MONEY;
        }
        return revenue.divide(BigDecimal.valueOf(salesCount), 2, RoundingMode.HALF_UP);
    }

    private CurrentTurnResponse toCurrentTurnResponse(TurnEntity turn) {
        return new CurrentTurnResponse(turn.getId(), turn.getOpenedAt());
    }

    private RecentSaleResponse toRecentSaleResponse(RecentSaleProjection sale) {
        return new RecentSaleResponse(sale.getId(), sale.getCreatedAt(), sale.getTotal());
    }
}
