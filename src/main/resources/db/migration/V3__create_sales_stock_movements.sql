CREATE TABLE sales (
    id BIGSERIAL PRIMARY KEY,
    total NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sales_total_non_negative CHECK (total >= 0),
    CONSTRAINT chk_sales_status_valid CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE sale_items (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    subtotal NUMERIC(10,2) NOT NULL,
    CONSTRAINT fk_sale_items_sale_id FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT fk_sale_items_product_id FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_sale_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_sale_items_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT chk_sale_items_subtotal_non_negative CHECK (subtotal >= 0)
);

CREATE INDEX idx_sale_items_sale_id ON sale_items (sale_id);
CREATE INDEX idx_sale_items_product_id ON sale_items (product_id);

CREATE TABLE stock_movements (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    reference_sale_id BIGINT,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_stock_movements_product_id FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_stock_movements_reference_sale_id FOREIGN KEY (reference_sale_id) REFERENCES sales(id),
    CONSTRAINT chk_stock_movements_movement_type_valid CHECK (movement_type IN ('SALE', 'ENTRY', 'ADJUSTMENT')),
    CONSTRAINT chk_stock_movements_quantity_non_zero CHECK (quantity <> 0)
);

CREATE INDEX idx_stock_movements_product_id ON stock_movements (product_id);
CREATE INDEX idx_stock_movements_created_at ON stock_movements (created_at);

