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
public class TurnResponse {

    private UUID id;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private String operatorName;
    private TurnStatus status;
    private String openingNote;
    private String closingNote;
    private Long durationInMinutes;
}

