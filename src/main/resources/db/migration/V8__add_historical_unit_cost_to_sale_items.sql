ALTER TABLE sale_items
    ADD COLUMN unit_cost_price NUMERIC(10, 2) CHECK (unit_cost_price >= 0);
