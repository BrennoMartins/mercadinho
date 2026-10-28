ALTER TABLE sales
    ADD COLUMN subtotal NUMERIC(10,2),
    ADD COLUMN discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0;

-- Historical sales had no discount: subtotal equals the stored total.
UPDATE sales
SET subtotal = total;

ALTER TABLE sales
    ALTER COLUMN subtotal SET NOT NULL;

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_subtotal_non_negative CHECK (subtotal >= 0),
    ADD CONSTRAINT chk_sales_discount_amount_non_negative CHECK (discount_amount >= 0),
    ADD CONSTRAINT chk_sales_discount_amount_not_above_subtotal CHECK (discount_amount <= subtotal),
    ADD CONSTRAINT chk_sales_total_equals_subtotal_minus_discount CHECK (total = subtotal - discount_amount);
