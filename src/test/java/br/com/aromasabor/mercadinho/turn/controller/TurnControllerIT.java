package br.com.aromasabor.mercadinho.turn.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.aromasabor.mercadinho.product.exception.GlobalExceptionHandler;
import br.com.aromasabor.mercadinho.turn.dto.CloseTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.CloseTurnResponse;
import br.com.aromasabor.mercadinho.turn.dto.OpenTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.TurnResponse;
import br.com.aromasabor.mercadinho.turn.dto.TurnSummaryResponse;
import br.com.aromasabor.mercadinho.turn.dto.TopSellingProductResponse;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.exception.NoOpenTurnException;
import br.com.aromasabor.mercadinho.turn.exception.TurnAlreadyOpenException;
import br.com.aromasabor.mercadinho.turn.exception.TurnNotFoundException;
import br.com.aromasabor.mercadinho.turn.service.TurnService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TurnControllerIT {

    private MockMvc mockMvc;
    private TurnService turnService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        turnService = mock(TurnService.class);
        TurnController controller = new TurnController(turnService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldOpenTurn() throws Exception {
        UUID turnId = UUID.randomUUID();

        OpenTurnRequest request = new OpenTurnRequest("Maria", "Inicio do expediente.");
        TurnResponse response = new TurnResponse(
                turnId,
                LocalDateTime.now(),
                null,
                "Maria",
                TurnStatus.OPEN,
                "Inicio do expediente.",
                null,
                null
        );

        when(turnService.open(any(OpenTurnRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/turns/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(turnId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void shouldRejectOpeningWhenThereIsAlreadyOpenTurn() throws Exception {
        OpenTurnRequest request = new OpenTurnRequest("Maria", "Inicio do expediente.");

        when(turnService.open(any(OpenTurnRequest.class)))
                .thenThrow(new TurnAlreadyOpenException("There is already an open turn."));

        mockMvc.perform(post("/api/turns/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("There is already an open turn."));
    }

    @Test
    void shouldReturnCurrentOpenTurn() throws Exception {
        UUID turnId = UUID.randomUUID();
        TurnResponse response = new TurnResponse(
                turnId,
                LocalDateTime.now(),
                null,
                "Maria",
                TurnStatus.OPEN,
                "Inicio do expediente.",
                null,
                null
        );

        when(turnService.findCurrentOpen()).thenReturn(response);

        mockMvc.perform(get("/api/turns/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(turnId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void shouldReturn404WhenNoOpenTurnExists() throws Exception {
        when(turnService.findCurrentOpen())
                .thenThrow(new NoOpenTurnException("No open turn found."));

        mockMvc.perform(get("/api/turns/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No open turn found."));
    }

    @Test
    void shouldCloseTurn() throws Exception {
        UUID turnId = UUID.randomUUID();
        CloseTurnRequest request = new CloseTurnRequest("Fechamento do dia.");
        CloseTurnResponse response = new CloseTurnResponse(
                turnId,
                LocalDateTime.now().minusHours(8),
                LocalDateTime.now(),
                TurnStatus.CLOSED,
                "Fechamento do dia.",
                480L
        );

        when(turnService.close(eq(turnId), any(CloseTurnRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/turns/{id}/close", turnId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.durationInMinutes").value(480));
    }

    @Test
    void shouldListTurnHistory() throws Exception {
        UUID turnId = UUID.randomUUID();
        TurnResponse response = new TurnResponse(
                turnId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1).plusHours(8),
                "Maria",
                TurnStatus.CLOSED,
                "Inicio do expediente.",
                "Fechamento do dia.",
                480L
        );

        when(turnService.findAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/turns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(turnId.toString()))
                .andExpect(jsonPath("$[0].status").value("CLOSED"));
    }

    @Test
    void shouldReturnTurnSummary() throws Exception {
        UUID turnId = UUID.randomUUID();
        TurnSummaryResponse response = new TurnSummaryResponse(
                turnId,
                TurnStatus.CLOSED,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now(),
                120L,
                2L,
                new BigDecimal("85.05"),
                new BigDecimal("42.53"),
                List.of(new TopSellingProductResponse(1L, "Arroz", 5L, new BigDecimal("50.00"))),
                3L
        );
        when(turnService.getSummary(turnId)).thenReturn(response);

        mockMvc.perform(get("/api/turns/{id}/summary", turnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnId").value(turnId.toString()))
                .andExpect(jsonPath("$.durationInMinutes").value(120))
                .andExpect(jsonPath("$.salesCount").value(2))
                .andExpect(jsonPath("$.totalRevenue").value(85.05))
                .andExpect(jsonPath("$.averageTicket").value(42.53))
                .andExpect(jsonPath("$.topProducts[0].productName").value("Arroz"))
                .andExpect(jsonPath("$.lowStockProductCount").value(3));
    }

    @Test
    void shouldReturn404WhenTurnForSummaryDoesNotExist() throws Exception {
        UUID turnId = UUID.randomUUID();
        when(turnService.getSummary(turnId))
                .thenThrow(new TurnNotFoundException("Turn not found with id: " + turnId));

        mockMvc.perform(get("/api/turns/{id}/summary", turnId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Turn not found with id: " + turnId));
    }
}
