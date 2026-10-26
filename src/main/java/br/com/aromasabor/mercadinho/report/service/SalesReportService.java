package br.com.aromasabor.mercadinho.report.service;

import br.com.aromasabor.mercadinho.report.dto.DailySalesReportEntry;
import br.com.aromasabor.mercadinho.report.dto.SalesReportResponse;
import br.com.aromasabor.mercadinho.report.exception.InvalidSalesReportPeriodException;
import br.com.aromasabor.mercadinho.sale.repository.SaleRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesReportProjection;
import br.com.aromasabor.mercadinho.sale.repository.projection.DailySalesSummaryProjection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesReportService {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final SaleRepository saleRepository;

    public SalesReportService(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SalesReportResponse getReport(String startDateValue, String endDateValue) {
        LocalDate startDate = parseDate(startDateValue, "startDate");
        LocalDate endDate = parseDate(endDateValue, "endDate");
        if (startDate.isAfter(endDate)) {
            throw new InvalidSalesReportPeriodException("startDate não pode ser posterior a endDate.");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end;
        try {
            end = endDate.plusDays(1).atStartOfDay();
        } catch (DateTimeException ex) {
            throw new InvalidSalesReportPeriodException("endDate não possui um dia seguinte representável.");
        }

        DailySalesSummaryProjection summary = saleRepository.getDailySummary(start, end);
        long salesCount = summary == null || summary.getSalesCount() == null
                ? 0L
                : summary.getSalesCount();
        BigDecimal revenue = summary == null || summary.getTotalRevenue() == null
                ? ZERO_MONEY
                : summary.getTotalRevenue();

        List<DailySalesReportEntry> dailySales = buildDailySales(startDate, endDate,
                saleRepository.getDailySalesReport(start, end));

        SalesReportResponse response = new SalesReportResponse();
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setTotalRevenue(revenue);
        response.setSalesCount(salesCount);
        response.setAverageTicket(calculateAverageTicket(revenue, salesCount));
        response.setDailySales(dailySales);
        return response;
    }

    private LocalDate parseDate(String value, String parameterName) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new InvalidSalesReportPeriodException(
                    parameterName + " é obrigatório e deve estar no formato yyyy-MM-dd.");
        }

        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new InvalidSalesReportPeriodException(parameterName + " não é uma data válida.");
        }
    }

    private List<DailySalesReportEntry> buildDailySales(
            LocalDate startDate,
            LocalDate endDate,
            List<DailySalesReportProjection> dailySummaries) {
        Map<LocalDate, DailySalesReportProjection> summariesByDate = new HashMap<>();
        for (DailySalesReportProjection dailySummary : dailySummaries) {
            summariesByDate.put(dailySummary.getSaleDate(), dailySummary);
        }

        List<DailySalesReportEntry> dailySales = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DailySalesReportProjection summary = summariesByDate.get(date);
            dailySales.add(new DailySalesReportEntry(
                    date,
                    summary == null || summary.getRevenue() == null ? ZERO_MONEY : summary.getRevenue(),
                    summary == null || summary.getSalesCount() == null ? 0L : summary.getSalesCount()));
        }
        return dailySales;
    }

    private BigDecimal calculateAverageTicket(BigDecimal revenue, long salesCount) {
        if (salesCount == 0) {
            return ZERO_MONEY;
        }
        return revenue.divide(BigDecimal.valueOf(salesCount), 2, RoundingMode.HALF_UP);
    }
}
