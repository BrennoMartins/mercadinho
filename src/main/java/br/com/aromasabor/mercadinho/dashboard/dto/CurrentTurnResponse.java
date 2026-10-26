package br.com.aromasabor.mercadinho.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Turno aberto atualmente, ou null quando não houver turno aberto")
public class CurrentTurnResponse {

    @Schema(description = "Identificador UUID do turno aberto")
    private UUID id;

    @Schema(description = "Data e hora local de abertura")
    private LocalDateTime openedAt;
}
