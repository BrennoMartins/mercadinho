ALTER TABLE stock_movements
    DROP CONSTRAINT chk_stock_movements_movement_type_valid;

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_movement_type_valid
        CHECK (movement_type IN ('SALE', 'ENTRY', 'ADJUSTMENT', 'DISCARD'));

ALTER TABLE stock_movements
    ADD COLUMN discard_reason VARCHAR(30);

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_discard_details_valid
        CHECK (
            (movement_type = 'DISCARD'
                AND discard_reason IS NOT NULL
                AND discard_reason IN ('DAMAGED', 'EXPIRED', 'UNFIT_FOR_SALE', 'OTHER')
                AND quantity < 0
                AND reference_sale_id IS NULL)
            OR (movement_type <> 'DISCARD' AND discard_reason IS NULL)
        );
