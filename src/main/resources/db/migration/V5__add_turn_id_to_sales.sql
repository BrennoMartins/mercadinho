ALTER TABLE sales
    ADD COLUMN turn_id UUID;

INSERT INTO turns (id, opened_at, closed_at, operator_name, status, opening_note, closing_note)
SELECT
    '00000000-0000-0000-0000-000000000001',
    COALESCE(MIN(created_at), NOW()),
    COALESCE(MAX(created_at), NOW()),
    'Sistema',
    'CLOSED',
    'Turno legado para vincular vendas existentes',
    'Gerado automaticamente na migration V5'
FROM sales
WHERE EXISTS (SELECT 1 FROM sales WHERE turn_id IS NULL)
ON CONFLICT (id) DO NOTHING;

UPDATE sales
SET turn_id = '00000000-0000-0000-0000-000000000001'
WHERE turn_id IS NULL;

ALTER TABLE sales
    ALTER COLUMN turn_id SET NOT NULL;

ALTER TABLE sales
    ADD CONSTRAINT fk_sales_turn_id
    FOREIGN KEY (turn_id) REFERENCES turns(id);

CREATE INDEX idx_sales_turn_id ON sales (turn_id);

