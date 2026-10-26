package br.com.aromasabor.mercadinho.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockReplenishmentRequestDTO {

    @NotNull(message = "é obrigatório")
    @Positive(message = "deve ser maior que zero")
    private Integer quantity;

    @Size(max = 255, message = "deve ter no máximo 255 caracteres")
    private String reason;
}

