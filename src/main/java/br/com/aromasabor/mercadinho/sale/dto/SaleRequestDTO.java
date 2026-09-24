package br.com.aromasabor.mercadinho.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
}

