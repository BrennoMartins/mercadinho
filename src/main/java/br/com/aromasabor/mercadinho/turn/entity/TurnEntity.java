package br.com.aromasabor.mercadinho.turn.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "turns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "operator_name", nullable = false, length = 100)
    private String operatorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TurnStatus status;

    @Column(name = "opening_note")
    private String openingNote;

    @Column(name = "closing_note")
    private String closingNote;
}

