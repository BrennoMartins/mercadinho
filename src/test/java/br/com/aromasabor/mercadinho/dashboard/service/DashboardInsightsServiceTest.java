package br.com.aromasabor.mercadinho.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DashboardInsightsServiceTest {

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private ProductRepository productRepository;

    private DashboardInsightsService dashboardInsightsService;

    @BeforeEach
    void setUp() {
        dashboardInsightsService = new DashboardInsightsService(saleItemRepository, productRepository);
    }

    @Test
    void shouldQueryLastSevenCalendarDaysIncludingTodayAndMapTopProducts() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDateTime start = today.minusDays(6).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        when(saleItemRepository.findTopProductsBetween(start, end, PageRequest.of(0, 5)))
                .thenReturn(List.of(new TopProduct(9L, "Café", 12L)));

        List<TopProductResponse> response = dashboardInsightsService.getTopProducts();

        assertThat(response).singleElement().satisfies(product -> {
            assertThat(product.getProductId()).isEqualTo(9L);
            assertThat(product.getProductName()).isEqualTo("Café");
            assertThat(product.getQuantitySold()).isEqualTo(12L);
        });
        verify(saleItemRepository).findTopProductsBetween(eq(start), eq(end), eq(PageRequest.of(0, 5)));
    }

    @Test
    void shouldReturnAnEmptyTopProductsListWhenRepositoryHasNoEligibleSales() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        when(saleItemRepository.findTopProductsBetween(
                today.minusDays(6).atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                PageRequest.of(0, 5))).thenReturn(List.of());

        assertThat(dashboardInsightsService.getTopProducts()).isEmpty();
    }

    @Test
    void shouldUseTheSharedLowStockThresholdAndMapProductFields() {
        LowStockProductProjection product = new LowStockProductProjection() {
            @Override
            public Long getId() {
                return 42L;
            }

            @Override
            public String getName() {
                return "Arroz";
            }

            @Override
            public String getBarcode() {
                return "789123";
            }

            @Override
            public Integer getStock() {
                return 0;
            }
        };
        when(productRepository.findLowStockProducts(ProductStockPolicy.LOW_STOCK_THRESHOLD))
                .thenReturn(List.of(product));

        List<LowStockProductResponse> response = dashboardInsightsService.getLowStockProducts();

        assertThat(response).singleElement().satisfies(lowStockProduct -> {
            assertThat(lowStockProduct.getId()).isEqualTo(42L);
            assertThat(lowStockProduct.getName()).isEqualTo("Arroz");
            assertThat(lowStockProduct.getBarcode()).isEqualTo("789123");
            assertThat(lowStockProduct.getStock()).isZero();
        });
        verify(productRepository).findLowStockProducts(ProductStockPolicy.LOW_STOCK_THRESHOLD);
    }

    private record TopProduct(Long productId, String productName, Long quantitySold)
            implements DashboardTopProductProjection {

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
    }
}
