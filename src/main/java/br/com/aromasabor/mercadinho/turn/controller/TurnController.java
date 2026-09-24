package br.com.aromasabor.mercadinho.turn.controller;

import br.com.aromasabor.mercadinho.turn.dto.CloseTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.CloseTurnResponse;
import br.com.aromasabor.mercadinho.turn.dto.OpenTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.TurnResponse;
import br.com.aromasabor.mercadinho.turn.service.TurnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/turns")
@RequiredArgsConstructor
@Tag(name = "Turns")
public class TurnController {

    private final TurnService turnService;

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open market turn")
    @ApiResponse(responseCode = "201", description = "Turn opened successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "There is already an open turn")
    public TurnResponse open(@Valid @RequestBody OpenTurnRequest request) {
        return turnService.open(request);
    }

    @GetMapping("/current")
    @Operation(summary = "Get current open turn")
    @ApiResponse(responseCode = "200", description = "Open turn found")
    @ApiResponse(responseCode = "404", description = "No open turn")
    public TurnResponse findCurrentOpen() {
        return turnService.findCurrentOpen();
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close turn")
    @ApiResponse(responseCode = "200", description = "Turn closed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "404", description = "Open turn not found")
    public CloseTurnResponse close(@PathVariable UUID id, @Valid @RequestBody CloseTurnRequest request) {
        return turnService.close(id, request);
    }

    @GetMapping
    @Operation(summary = "List turns history")
    @ApiResponse(responseCode = "200", description = "Turns listed successfully")
    public List<TurnResponse> findAll() {
        return turnService.findAll();
    }
}

