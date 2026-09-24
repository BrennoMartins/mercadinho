package br.com.aromasabor.mercadinho.turn.dto;

import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
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
public class CloseTurnResponse {

    private UUID id;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private TurnStatus status;
    private String closingNote;
    private Long durationInMinutes;
}

