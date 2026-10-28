package br.com.aromasabor.mercadinho.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaleResponseDTO {

    private Long id;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private String status;
    private LocalDateTime createdAt;
    private List<SaleItemResponseDTO> items;
    @Schema(description = "Lucro bruto conhecido: soma das margens dos itens com custo histórico menos o desconto "
            + "geral da venda. Pode ser negativo quando o desconto supera a margem conhecida, principalmente "
            + "em vendas com custo incompleto.")
    private BigDecimal grossProfitKnownAmount;
    private Boolean grossProfitComplete;
    private Long itemsWithoutHistoricalCost;

    public SaleResponseDTO(Long id, BigDecimal total, String status, LocalDateTime createdAt,
                           List<SaleItemResponseDTO> items, BigDecimal grossProfitKnownAmount,
                           Boolean grossProfitComplete, Long itemsWithoutHistoricalCost) {
        this(id, total, BigDecimal.ZERO, total, status, createdAt, items, grossProfitKnownAmount,
                grossProfitComplete, itemsWithoutHistoricalCost);
    }
}
