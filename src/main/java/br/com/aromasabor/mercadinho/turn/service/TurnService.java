package br.com.aromasabor.mercadinho.turn.service;

import br.com.aromasabor.mercadinho.turn.dto.CloseTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.CloseTurnResponse;
import br.com.aromasabor.mercadinho.turn.dto.OpenTurnRequest;
import br.com.aromasabor.mercadinho.turn.dto.TurnResponse;
import br.com.aromasabor.mercadinho.turn.entity.TurnEntity;
import br.com.aromasabor.mercadinho.turn.entity.TurnStatus;
import br.com.aromasabor.mercadinho.turn.exception.NoOpenTurnException;
import br.com.aromasabor.mercadinho.turn.exception.TurnAlreadyOpenException;
import br.com.aromasabor.mercadinho.turn.repository.TurnRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TurnService {

    private final TurnRepository turnRepository;

    public TurnService(TurnRepository turnRepository) {
        this.turnRepository = turnRepository;
    }

    @Transactional
    public TurnResponse open(OpenTurnRequest request) {
        if (turnRepository.existsByStatus(TurnStatus.OPEN)) {
            throw new TurnAlreadyOpenException("There is already an open turn.");
        }

        LocalDateTime now = LocalDateTime.now();
        TurnEntity turn = TurnEntity.builder()
                .id(UUID.randomUUID())
                .openedAt(now)
                .operatorName(request.getOperatorName())
                .status(TurnStatus.OPEN)
                .openingNote(request.getOpeningNote())
                .build();

        try {
            return toTurnResponse(turnRepository.save(turn));
        } catch (DataIntegrityViolationException ex) {
            throw new TurnAlreadyOpenException("There is already an open turn.");
        }
    }

    @Transactional(readOnly = true)
    public TurnResponse findCurrentOpen() {
        TurnEntity turn = turnRepository.findFirstByStatusOrderByOpenedAtDesc(TurnStatus.OPEN)
                .orElseThrow(() -> new NoOpenTurnException("No open turn found."));
        return toTurnResponse(turn);
    }

    @Transactional
    public CloseTurnResponse close(UUID id, CloseTurnRequest request) {
        TurnEntity turn = turnRepository.findByIdAndStatus(id, TurnStatus.OPEN)
                .orElseThrow(() -> new NoOpenTurnException("No open turn found with id: " + id));

        LocalDateTime closedAt = LocalDateTime.now();
        turn.setClosedAt(closedAt);
        turn.setStatus(TurnStatus.CLOSED);
        turn.setClosingNote(request.getClosingNote());

        TurnEntity savedTurn = turnRepository.save(turn);
        return toCloseTurnResponse(savedTurn);
    }

    @Transactional(readOnly = true)
    public List<TurnResponse> findAll() {
        return turnRepository.findAllByOrderByOpenedAtDesc().stream()
                .map(this::toTurnResponse)
                .toList();
    }

    private TurnResponse toTurnResponse(TurnEntity turn) {
        TurnResponse response = new TurnResponse();
        response.setId(turn.getId());
        response.setOpenedAt(turn.getOpenedAt());
        response.setClosedAt(turn.getClosedAt());
        response.setOperatorName(turn.getOperatorName());
        response.setStatus(turn.getStatus());
        response.setOpeningNote(turn.getOpeningNote());
        response.setClosingNote(turn.getClosingNote());
        response.setDurationInMinutes(calculateDurationInMinutes(turn.getOpenedAt(), turn.getClosedAt()));
        return response;
    }

    private CloseTurnResponse toCloseTurnResponse(TurnEntity turn) {
        CloseTurnResponse response = new CloseTurnResponse();
        response.setId(turn.getId());
        response.setOpenedAt(turn.getOpenedAt());
        response.setClosedAt(turn.getClosedAt());
        response.setStatus(turn.getStatus());
        response.setClosingNote(turn.getClosingNote());
        response.setDurationInMinutes(calculateDurationInMinutes(turn.getOpenedAt(), turn.getClosedAt()));
        return response;
    }

    private Long calculateDurationInMinutes(LocalDateTime openedAt, LocalDateTime closedAt) {
        if (openedAt == null || closedAt == null) {
            return null;
        }
        return Duration.between(openedAt, closedAt).toMinutes();
    }
}

