ALTER TABLE sales
    ALTER COLUMN subtotal TYPE NUMERIC(30, 2),
    ALTER COLUMN total TYPE NUMERIC(30, 2);

ALTER TABLE sale_items
    ALTER COLUMN subtotal TYPE NUMERIC(30, 2),
    ADD COLUMN product_name_snapshot VARCHAR(255),
    ADD COLUMN category_id_snapshot BIGINT,
    ADD COLUMN category_name_snapshot VARCHAR(100),
    ADD COLUMN discount_allocated_amount NUMERIC(30, 2);

ALTER TABLE sale_items
    ADD CONSTRAINT chk_sale_items_category_snapshot_pair
        CHECK ((category_id_snapshot IS NULL) = (category_name_snapshot IS NULL)),
    ADD CONSTRAINT chk_sale_items_discount_allocated_amount_valid
        CHECK (discount_allocated_amount IS NULL
            OR (discount_allocated_amount >= 0 AND discount_allocated_amount <= subtotal));
