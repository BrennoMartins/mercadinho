package br.com.aromasabor.mercadinho.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaleRequestDTO {

    @NotNull(message = "é obrigatório")
    @NotEmpty(message = "deve conter ao menos um item")
    @Valid
    private List<SaleItemRequestDTO> items;

    @PositiveOrZero(message = "deve ser maior ou igual a zero")
    @Digits(integer = 8, fraction = 2, message = "deve ter no máximo 2 casas decimais")
    @Schema(description = "Desconto geral em reais aplicado à venda inteira. Opcional; assume zero quando omitido. "
            + "Não pode superar o subtotal calculado pelo backend.", example = "5.00")
    private BigDecimal discountAmount;

    public SaleRequestDTO(List<SaleItemRequestDTO> items) {
        this(items, null);
    }
}

