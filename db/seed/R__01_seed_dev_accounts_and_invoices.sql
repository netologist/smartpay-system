-- ==============================================================================
-- Flyway Repeatable Migration: R__01_seed_dev_accounts_and_invoices.sql
-- Purpose: Developer Local Testing Fixtures & Seed Data
-- Environment: LOCAL DEVELOPMENT ONLY (classpath:db/seed)
-- Production Status: STRICTLY EXCLUDED IN PRODUCTION
-- ==============================================================================

-- 1. Seed Platform System Accounts
INSERT INTO accounts (id, account_number, entity_id, entity_type, currency)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'TEG-PLATFORM-ESCROW-001', '00000000-0000-0000-0000-000000000001', 'PLATFORM_ESCROW', 'GBP'),
    ('00000000-0000-0000-0000-000000000002', 'TEG-PLATFORM-FEE-001',    '00000000-0000-0000-0000-000000000002', 'PLATFORM_FEE',    'GBP')
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO account_balances (account_id, cleared_balance_pence, hold_balance_pence, version)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 0, 0, 0),
    ('00000000-0000-0000-0000-000000000002', 0, 0, 0)
ON CONFLICT (account_id) DO NOTHING;

-- 2. Seed Test Shipper (Load Poster) with £50,000 Cleared Balance (5,000,000 pence)
INSERT INTO accounts (id, account_number, entity_id, entity_type, currency)
VALUES 
    ('11111111-1111-1111-1111-111111111111', 'SHIPPER-DPD-PARTNER-001', 'aaaaaaaa-1111-1111-1111-111111111111', 'SHIPPER', 'GBP')
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO account_balances (account_id, cleared_balance_pence, hold_balance_pence, version)
VALUES 
    ('11111111-1111-1111-1111-111111111111', 5000000, 0, 0)
ON CONFLICT (account_id) DO UPDATE 
SET cleared_balance_pence = 5000000;

-- 3. Seed Test Carriers (Hauliers & Couriers)
INSERT INTO accounts (id, account_number, entity_id, entity_type, currency)
VALUES 
    ('22222222-2222-2222-2222-222222222221', 'CARRIER-EXPRESS-VAN-001', 'bbbbbbbb-2222-2222-2222-222222222221', 'CARRIER', 'GBP'),
    ('22222222-2222-2222-2222-222222222222', 'CARRIER-HEAVY-HAUL-002', 'bbbbbbbb-2222-2222-2222-222222222222', 'CARRIER', 'GBP')
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO account_balances (account_id, cleared_balance_pence, hold_balance_pence, version)
VALUES 
    ('22222222-2222-2222-2222-222222222221', 0, 0, 0),
    ('22222222-2222-2222-2222-222222222222', 0, 0, 0)
ON CONFLICT (account_id) DO NOTHING;

-- 4. Seed Sample Invoices for Batch Settlement Testing
INSERT INTO invoices (id, load_id, shipper_id, carrier_id, vehicle_type, mileage_miles, base_amount_pence, fuel_surcharge_pence, vat_amount_pence, total_amount_pence, status)
VALUES 
    ('33333333-3333-3333-3333-333333333331', 'LOAD-TEST-001', 'aaaaaaaa-1111-1111-1111-111111111111', 'bbbbbbbb-2222-2222-2222-222222222221', 'VAN',  50.00, 3000, 500, 700, 4200, 'APPROVED'),
    ('33333333-3333-3333-3333-333333333332', 'LOAD-TEST-002', 'aaaaaaaa-1111-1111-1111-111111111111', 'bbbbbbbb-2222-2222-2222-222222222222', '7_5T', 120.00, 5000, 1500, 1300, 7800, 'APPROVED')
ON CONFLICT (load_id) DO NOTHING;
