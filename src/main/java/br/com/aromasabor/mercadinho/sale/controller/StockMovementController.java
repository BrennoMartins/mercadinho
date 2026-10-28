package br.com.aromasabor.mercadinho.sale.controller;

import br.com.aromasabor.mercadinho.sale.dto.StockMovementResponseDTO;
import br.com.aromasabor.mercadinho.sale.service.StockMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-movements")
@Tag(name = "Stock movements")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    public StockMovementController(StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    @GetMapping
    @Operation(
            summary = "Consultar histórico de movimentações de estoque",
            description = "Lista as movimentações mais recentes primeiro. Datas são inclusivas; " +
                    "page começa em zero e size aceita valores de 1 a 100.")
    @ApiResponse(responseCode = "200", description = "Histórico consultado com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos")
    public Page<StockMovementResponseDTO> findHistory(
            @Parameter(description = "Filtra pelo identificador do produto", example = "7")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "Filtra por SALE, ENTRY, ADJUSTMENT ou DISCARD", example = "DISCARD")
            @RequestParam(required = false) String movementType,
            @Parameter(description = "Limite inicial inclusivo em ISO-8601", example = "2026-10-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "Limite final inclusivo em ISO-8601", example = "2026-10-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Índice da página, começando em zero", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade por página (1 a 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return stockMovementService.findHistory(productId, movementType, startDate, endDate, page, size);
    }
}
