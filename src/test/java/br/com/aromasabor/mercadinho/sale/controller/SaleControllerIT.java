package br.com.aromasabor.mercadinho.sale.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.exception.GlobalExceptionHandler;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleItemResponseDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleRequestDTO;
import br.com.aromasabor.mercadinho.sale.dto.SaleResponseDTO;
import br.com.aromasabor.mercadinho.sale.exception.MarketClosedException;
import br.com.aromasabor.mercadinho.sale.service.SaleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SaleControllerIT {

    private MockMvc mockMvc;
    private SaleService saleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        saleService = mock(SaleService.class);
        SaleController controller = new SaleController(saleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateSaleWhenThereIsOpenTurn() throws Exception {
        SaleRequestDTO request = new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 2)));

        SaleResponseDTO response = new SaleResponseDTO(
                50L,
                new BigDecimal("20.00"),
                "COMPLETED",
                LocalDateTime.now(),
                List.of(new SaleItemResponseDTO(1L, "Arroz", 2, new BigDecimal("10.00"), new BigDecimal("20.00")))
        );

        when(saleService.create(any(SaleRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturnConflictWhenMarketIsClosed() throws Exception {
        SaleRequestDTO request = new SaleRequestDTO(List.of(new SaleItemRequestDTO(1L, 2)));

        when(saleService.create(any(SaleRequestDTO.class))).thenThrow(
                new MarketClosedException("Não é possível registrar a venda porque o mercado está fechado."));

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Não é possível registrar a venda porque o mercado está fechado."));
    }
}

