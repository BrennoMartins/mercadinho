package br.com.aromasabor.mercadinho.turn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenTurnRequest {

    @NotBlank(message = "nao pode estar vazio")
    private String operatorName;

    private String openingNote;
}

