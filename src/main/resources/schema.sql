-- =============================================================================
-- ESQUEMA DE BASE DE DATOS: PEDIDO INTERNACIONAL (POSTGRESQL)
-- =============================================================================

CREATE TABLE IF NOT EXISTS orders (
    order_id           VARCHAR(64) PRIMARY KEY,
    customer_id        VARCHAR(64) NOT NULL,
    country_iso2       VARCHAR(2) NOT NULL,
    currency           VARCHAR(3) NOT NULL,
    total_original     NUMERIC(14, 2) NOT NULL,
    total_eur          NUMERIC(14, 2),
    exchange_rate      NUMERIC(12, 6),
    country_name       VARCHAR(150),
    country_region     VARCHAR(100),
    phone_prefix       VARCHAR(20),
    status             VARCHAR(30) NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);

CREATE TABLE IF NOT EXISTS order_events (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           VARCHAR(64) NOT NULL,
    event_type         VARCHAR(50) NOT NULL,
    payload            TEXT,
    correlation_id     VARCHAR(100) NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_events_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_order_events_order_id ON order_events(order_id);
CREATE INDEX IF NOT EXISTS idx_order_events_correlation_id ON order_events(correlation_id);