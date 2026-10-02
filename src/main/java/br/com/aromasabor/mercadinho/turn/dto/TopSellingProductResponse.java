package br.com.aromasabor.mercadinho.turn.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopSellingProductResponse {

    private Long productId;
    private String productName;
    private Long quantitySold;
    private BigDecimal revenue;
}
