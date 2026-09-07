-- ==============================================================================
-- Flyway Migration: V1__init_invoicing_and_epod.sql
-- Domain: Freight Loads, Electronic Proof of Delivery (ePOD), Digital Invoicing & VAT
-- Database: PostgreSQL 16
-- ==============================================================================

-- 1. Electronic Proof of Delivery (ePOD) Records
CREATE TABLE epod_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    load_id VARCHAR(64) UNIQUE NOT NULL,
    carrier_id UUID NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    photo_s3_url VARCHAR(512) NOT NULL,
    signature_hash VARCHAR(64) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_epod_load ON epod_records(load_id);
CREATE INDEX idx_epod_carrier ON epod_records(carrier_id, delivered_at DESC);

-- 2. Freight Invoices Table
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    load_id VARCHAR(64) UNIQUE NOT NULL,
    shipper_id UUID NOT NULL,
    carrier_id UUID NOT NULL,
    vehicle_type VARCHAR(16) NOT NULL,
    mileage_miles NUMERIC(8, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'GBP',
    base_amount_pence BIGINT NOT NULL,
    fuel_surcharge_pence BIGINT NOT NULL DEFAULT 0,
    vat_amount_pence BIGINT NOT NULL DEFAULT 0,
    total_amount_pence BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_vehicle_type CHECK (vehicle_type IN ('VAN', 'LUTON', '7_5T', 'ARTIC')),
    CONSTRAINT chk_mileage_positive CHECK (mileage_miles > 0),
    CONSTRAINT chk_base_amount CHECK (base_amount_pence >= 0),
    CONSTRAINT chk_total_amount CHECK (total_amount_pence > 0),
    CONSTRAINT chk_invoice_status CHECK (
        status IN ('DRAFT', 'EPOD_VERIFIED', 'AWAITING_APPROVAL', 'APPROVED', 'FACTORING_APPROVED', 'HELD_IN_ESCROW', 'SETTLED', 'PAID', 'DISPUTED', 'CANCELLED')
    )
);

CREATE INDEX idx_invoices_shipper_status ON invoices(shipper_id, status);
CREATE INDEX idx_invoices_carrier_status ON invoices(carrier_id, status);
CREATE INDEX idx_invoices_load ON invoices(load_id);

-- Trigger to update updated_at on invoices
CREATE OR REPLACE FUNCTION update_invoice_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoices_updated_at
BEFORE UPDATE ON invoices
FOR EACH ROW
EXECUTE FUNCTION update_invoice_timestamp();
