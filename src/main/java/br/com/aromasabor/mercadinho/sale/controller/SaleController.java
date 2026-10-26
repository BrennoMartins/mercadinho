package br.com.aromasabor.mercadinho.sale.controller;

import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleResponseDTO;
import br.com.aromasabor.mercadinho.sale.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@Tag(name = "Sales")
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Finalizar venda")
    @ApiResponse(responseCode = "201", description = "Venda finalizada com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    @ApiResponse(responseCode = "409", description = "Estoque insuficiente")
    public SaleResponseDTO create(@Valid @RequestBody SaleRequestDTO request) {
        return saleService.create(request);
    }

    @GetMapping
    @Operation(summary = "Listar vendas")
    @ApiResponse(responseCode = "200", description = "Vendas listadas com sucesso")
    public List<SaleResponseDTO> findAll() {
        return saleService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar venda por ID")
    @ApiResponse(responseCode = "200", description = "Venda encontrada")
    @ApiResponse(responseCode = "404", description = "Venda não encontrada")
    public SaleResponseDTO findById(@PathVariable Long id) {
        return saleService.findById(id);
    }
}

