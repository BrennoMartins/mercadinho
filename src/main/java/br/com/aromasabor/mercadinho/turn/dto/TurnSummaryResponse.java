package br.com.aromasabor.mercadinho.turn.dto;

import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TurnSummaryResponse {

    private UUID turnId;
    private TurnStatus status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Long durationInMinutes;
    private Long salesCount;
    private BigDecimal totalRevenue;
    private BigDecimal averageTicket;
    private List<TopSellingProductResponse> topProducts;
    private Long lowStockProductCount;
}
