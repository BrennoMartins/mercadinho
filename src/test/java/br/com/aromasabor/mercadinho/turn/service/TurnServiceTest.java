package br.com.aromasabor.mercadinho.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.aromasabor.mercadinho.turn.dto.CloseTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.CloseTurnResponse;
import br.com.aromasabor.mercadinho.turn.dto.OpenTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.TurnResponse;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.exception.NoOpenTurnException;
import br.com.aromasabor.mercadinho.turn.exception.TurnAlreadyOpenException;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TurnServiceTest {

    @Mock
    private TurnRepository turnRepository;

    @InjectMocks
    private TurnService turnService;

    @Test
    void shouldOpenTurnSuccessfully() {
        OpenTurnRequest request = new OpenTurnRequest("Maria", "Inicio do expediente.");

        when(turnRepository.existsByStatus(TurnStatus.OPEN)).thenReturn(false);
        when(turnRepository.save(any(TurnEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TurnResponse response = turnService.open(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getOperatorName()).isEqualTo("Maria");
        assertThat(response.getStatus()).isEqualTo(TurnStatus.OPEN);
        assertThat(response.getClosedAt()).isNull();
        verify(turnRepository).save(any(TurnEntity.class));
    }

    @Test
    void shouldNotAllowOpenWhenAnotherTurnIsOpen() {
        OpenTurnRequest request = new OpenTurnRequest("Maria", "Inicio do expediente.");

        when(turnRepository.existsByStatus(TurnStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> turnService.open(request))
                .isInstanceOf(TurnAlreadyOpenException.class)
                .hasMessage("There is already an open turn.");

        verify(turnRepository, never()).save(any(TurnEntity.class));
    }

    @Test
    void shouldHandleConcurrentOpenAttempt() {
        OpenTurnRequest request = new OpenTurnRequest("Maria", "Inicio do expediente.");

        when(turnRepository.existsByStatus(TurnStatus.OPEN)).thenReturn(false);
        when(turnRepository.save(any(TurnEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> turnService.open(request))
                .isInstanceOf(TurnAlreadyOpenException.class)
                .hasMessage("There is already an open turn.");
    }

    @Test
    void shouldCloseOpenTurnAndCalculateDuration() {
        UUID id = UUID.randomUUID();
        LocalDateTime openedAt = LocalDateTime.now().minusHours(2);

        TurnEntity openTurn = TurnEntity.builder()
                .id(id)
                .openedAt(openedAt)
                .operatorName("Maria")
                .status(TurnStatus.OPEN)
                .openingNote("Inicio do expediente.")
                .build();

        CloseTurnRequest request = new CloseTurnRequest("Fechamento do dia.");

        when(turnRepository.findByIdAndStatus(id, TurnStatus.OPEN)).thenReturn(Optional.of(openTurn));
        when(turnRepository.save(any(TurnEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CloseTurnResponse response = turnService.close(id, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TurnStatus.CLOSED);
        assertThat(response.getClosedAt()).isNotNull();
        assertThat(response.getDurationInMinutes()).isGreaterThanOrEqualTo(120L);
        assertThat(response.getClosingNote()).isEqualTo("Fechamento do dia.");
    }

    @Test
    void shouldThrowWhenClosingInexistentOpenTurn() {
        UUID id = UUID.randomUUID();
        CloseTurnRequest request = new CloseTurnRequest("Fechamento do dia.");

        when(turnRepository.findByIdAndStatus(id, TurnStatus.OPEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> turnService.close(id, request))
                .isInstanceOf(NoOpenTurnException.class)
                .hasMessage("No open turn found with id: " + id);
    }
}

