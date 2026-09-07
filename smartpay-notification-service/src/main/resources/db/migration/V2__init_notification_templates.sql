-- ==============================================================================
-- Flyway Migration: V2__init_notification_templates.sql (STORY-008: Table V10)
-- Domain: Dynamic Notification Templates (SMS, Email, Webhook)
-- Database: PostgreSQL 16
-- Schema: notification
-- ==============================================================================

CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    subject VARCHAR(255) NULL,
    body_template TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_template_channel CHECK (channel IN ('SMS', 'EMAIL', 'WEBHOOK')),
    CONSTRAINT uq_template_code_channel UNIQUE (template_code, channel)
);

CREATE INDEX idx_notification_templates_lookup ON notification_templates(template_code, channel);

-- Seed default templates
INSERT INTO notification_templates (template_code, channel, subject, body_template) VALUES
('PAYMENT_SETTLED', 'SMS', NULL, 'SmartPay: Payout of {{formattedAmount}} settled to {{carrierName}}. Bank Ref: {{bankRef}}.'),
('PAYMENT_SETTLED', 'EMAIL', 'SmartPay: Payout Settled - {{bankRef}}', 'Dear {{carrierName}},\n\nYour net payout of {{formattedAmount}} has been settled successfully to your registered bank account.\nReference: {{bankRef}}\n\nThank you for partnering with SmartPay Logistics.'),
('PAYMENT_SETTLED', 'WEBHOOK', NULL, '{"event":"PAYMENT_SETTLED","paymentId":"{{paymentId}}","carrierId":"{{carrierId}}","amount":"{{formattedAmount}}","bankRef":"{{bankRef}}","carrierName":"{{carrierName}}","timestamp":"{{timestamp}}"}'),

('INVOICE_ISSUED', 'SMS', NULL, 'SmartPay: Invoice {{invoiceId}} issued for {{formattedAmount}} on load {{loadId}}. Due in 30 days.'),
('INVOICE_ISSUED', 'EMAIL', 'SmartPay: Freight Invoice Issued - {{invoiceId}}', 'Dear Shipper,\n\nInvoice {{invoiceId}} for load {{loadId}} has been finalized with total gross freight of {{formattedAmount}}.\nPayment is due according to standard net terms.\n\nSmartPay Automated Invoicing.'),
('INVOICE_ISSUED', 'WEBHOOK', NULL, '{"event":"INVOICE_ISSUED","invoiceId":"{{invoiceId}}","loadId":"{{loadId}}","shipperId":"{{shipperId}}","amount":"{{formattedAmount}}","timestamp":"{{timestamp}}"}'),

('FACTORING_PAYOUT_APPROVED', 'SMS', NULL, 'SmartPay: Factoring payout of {{formattedAmount}} approved for invoice {{invoiceId}}. Instant advance initiated.'),
('FACTORING_PAYOUT_APPROVED', 'EMAIL', 'SmartPay: Factoring Payout Approved - {{invoiceId}}', 'Dear Carrier,\n\nYour factoring request for invoice {{invoiceId}} has been approved. Net disbursement of {{formattedAmount}} has been scheduled.\n\nSmartPay Capital Liquidity Desk.'),
('FACTORING_PAYOUT_APPROVED', 'WEBHOOK', NULL, '{"event":"FACTORING_PAYOUT_APPROVED","invoiceId":"{{invoiceId}}","carrierId":"{{carrierId}}","payoutAmount":"{{formattedAmount}}","timestamp":"{{timestamp}}"}');
