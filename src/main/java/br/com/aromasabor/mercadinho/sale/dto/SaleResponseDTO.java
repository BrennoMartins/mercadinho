package br.com.aromasabor.mercadinho.sale.dto;

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
    private BigDecimal total;
    private String status;
    private LocalDateTime createdAt;
    private List<SaleItemResponseDTO> items;
}

