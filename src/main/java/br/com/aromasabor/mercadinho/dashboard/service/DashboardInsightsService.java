package br.com.aromasabor.mercadinho.dashboard.service;

import br.com.aromasabor.mercadinho.dashboard.dto.LowStockProductResponse;
import br.com.aromasabor.mercadinho.dashboard.dto.TopProductResponse;
import br.com.aromasabor.mercadinho.product.repository.ProductRepository;
import br.com.aromasabor.mercadinho.product.repository.projection.LowStockProductProjection;
import br.com.aromasabor.mercadinho.product.service.ProductStockPolicy;
import br.com.aromasabor.mercadinho.sale.repository.SaleItemRepository;
import br.com.aromasabor.mercadinho.sale.repository.projection.DashboardTopProductProjection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardInsightsService {

    private static final int TOP_PRODUCTS_LIMIT = 5;

    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;

    public DashboardInsightsService(SaleItemRepository saleItemRepository, ProductRepository productRepository) {
        this.saleItemRepository = saleItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<TopProductResponse> getTopProducts() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDateTime start = today.minusDays(6).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        return saleItemRepository.findTopProductsBetween(start, end, PageRequest.of(0, TOP_PRODUCTS_LIMIT))
                .stream()
                .map(this::toTopProductResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LowStockProductResponse> getLowStockProducts() {
        return productRepository.findLowStockProducts(ProductStockPolicy.LOW_STOCK_THRESHOLD)
                .stream()
                .map(this::toLowStockProductResponse)
                .toList();
    }

    private TopProductResponse toTopProductResponse(DashboardTopProductProjection product) {
        return new TopProductResponse(product.getProductId(), product.getProductName(), product.getQuantitySold());
    }

    private LowStockProductResponse toLowStockProductResponse(LowStockProductProjection product) {
        return new LowStockProductResponse(product.getId(), product.getName(), product.getBarcode(), product.getStock());
    }
}
