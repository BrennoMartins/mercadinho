ALTER TABLE products
    ADD COLUMN cost_price NUMERIC(10, 2) CHECK (cost_price >= 0);
