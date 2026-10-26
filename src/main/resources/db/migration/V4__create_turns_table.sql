CREATE TABLE turns (
    id UUID PRIMARY KEY,
    opened_at TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMP,
    operator_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    opening_note TEXT,
    closing_note TEXT,
    CONSTRAINT chk_turns_status_valid CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_turns_closed_after_opened CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

CREATE INDEX idx_turns_status ON turns (status);
CREATE INDEX idx_turns_opened_at ON turns (opened_at);

CREATE UNIQUE INDEX uq_turns_open_status ON turns (status) WHERE status = 'OPEN';

