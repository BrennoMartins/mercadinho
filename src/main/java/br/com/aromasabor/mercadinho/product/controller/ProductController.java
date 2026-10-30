package br.com.aromasabor.mercadinho.product.controller;

import br.com.aromasabor.mercadinho.product.dto.ProductRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductCatalogResponseDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductResponseDTO;
import br.com.aromasabor.mercadinho.product.dto.ProductUpdateRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.StockAdjustmentRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.StockDiscardRequestDTO;
import br.com.aromasabor.mercadinho.product.dto.StockReplenishmentRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.StockMovementResponseDTO;
import br.com.aromasabor.mercadinho.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar produto")
    @ApiResponse(responseCode = "201", description = "Produto cadastrado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
    @ApiResponse(responseCode = "409", description = "Produto já cadastrado")
    public ProductResponseDTO create(@Valid @RequestBody ProductRequestDTO request) {
        return productService.create(request);
    }

    @GetMapping
    @Operation(summary = "Listar produtos ativos")
    @ApiResponse(responseCode = "200", description = "Produtos listados com sucesso")
    public List<ProductResponseDTO> findAll() {
        return productService.findAll();
    }

    @GetMapping("/inactive")
    @Operation(summary = "Listar produtos inativos",
            description = "Retorna somente produtos inativos, ordenados pelo ID em ordem crescente.")
    @ApiResponse(responseCode = "200", description = "Produtos inativos listados com sucesso")
    public List<ProductResponseDTO> findInactive() {
        return productService.findInactive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar produto por ID")
    @ApiResponse(responseCode = "200", description = "Produto encontrado")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    public ProductResponseDTO findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @GetMapping("/barcode/{barcode}")
    @CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173", "null"})
    @Operation(summary = "Buscar produto por código de barras")
    @ApiResponse(responseCode = "200", description = "Produto encontrado")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    public ProductResponseDTO findByBarcode(@PathVariable String barcode) {
        return productService.findByBarcode(barcode);
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar produtos ativos por nome",
            description = "Pesquisa parcial sem distinção entre maiúsculas/minúsculas e retorna no máximo "
                    + "dez produtos em ordem alfabética.")
    @ApiResponse(responseCode = "200", description = "Produtos encontrados")
    public List<ProductResponseDTO> searchByName(@RequestParam String name) {
        return productService.searchByName(name);
    }

    @GetMapping("/catalog")
    @Operation(
            summary = "Consultar catálogo administrativo de produtos",
            description = "Pesquisa produtos ativos ou inativos por trecho do nome ou código de barras exato, "
                    + "com filtros por categoria e paginação.")
    @ApiResponse(responseCode = "200", description = "Página de produtos retornada")
    @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos")
    public ProductCatalogResponseDTO searchCatalog(
            @RequestParam("search") String search,
            @RequestParam("active") boolean active,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return productService.searchCatalog(search, categoryId, active, page, size);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar dados cadastrais do produto",
            description = "O estoque não é atualizado por esta operação. Use reposição ou ajuste manual.")
    @ApiResponse(responseCode = "200", description = "Produto atualizado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Produto ou categoria não encontrado")
    @ApiResponse(responseCode = "409", description = "Barcode já cadastrado")
    public ProductResponseDTO update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequestDTO request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desativar produto",
            description = "Desativa o produto sem excluir o cadastro ou seu histórico.")
    @ApiResponse(responseCode = "204", description = "Produto desativado com sucesso")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reativar produto",
            description = "Reativa o produto existente sem alterar estoque ou dados cadastrais.")
    @ApiResponse(responseCode = "200", description = "Produto reativado com sucesso")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    @ApiResponse(responseCode = "409", description = "Produto já está ativo ou código de barras em conflito")
    public ProductResponseDTO reactivate(@PathVariable Long id) {
        return productService.reactivate(id);
    }

    @PostMapping("/{id}/replenishments")
    @Operation(summary = "Registrar reposição de estoque")
    @ApiResponse(responseCode = "200", description = "Estoque atualizado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    public ProductResponseDTO replenishStock(@PathVariable Long id,
                                             @Valid @RequestBody StockReplenishmentRequestDTO request) {
        return productService.replenishStock(id, request);
    }

    @PostMapping("/{id}/stock-adjustments")
    @Operation(
            summary = "Ajustar estoque à quantidade física contada",
            description = "Registra a diferença entre o saldo atual e a quantidade física final informada, " +
                    "junto com a justificativa. Ajustes sem diferença são rejeitados.")
    @ApiResponse(responseCode = "200", description = "Estoque ajustado e movimentação registrada")
    @ApiResponse(responseCode = "400", description = "Quantidade ou justificativa inválida")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    @ApiResponse(responseCode = "409", description = "Quantidade informada igual ao saldo atual")
    public ProductResponseDTO adjustStock(@PathVariable Long id,
                                          @Valid @RequestBody StockAdjustmentRequestDTO request) {
        return productService.adjustStock(id, request);
    }

    @PostMapping("/{id}/discards")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar descarte de estoque",
            description = "Reduz o estoque e registra o motivo e a observação no histórico de movimentações.")
    @ApiResponse(responseCode = "201", description = "Descarte registrado com sucesso")
    @ApiResponse(responseCode = "400", description = "Quantidade ou motivo inválido")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    @ApiResponse(responseCode = "409", description = "Quantidade superior ao estoque disponível")
    public StockMovementResponseDTO discardStock(@PathVariable Long id,
                                                 @Valid @RequestBody StockDiscardRequestDTO request) {
        return productService.discardStock(id, request);
    }
}
