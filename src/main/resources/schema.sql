-- Sri Karthikeya Caterers Database Schema
-- PostgreSQL 14+
--
-- Design philosophy:
--   This schema is intentionally lean. We collapse 1:N child tables into
--   JSONB columns on the parent when (a) children are always loaded with
--   the parent and (b) we never run cross-parent queries against the
--   children. This trades a small amount of indexability for a much
--   simpler operational shape — fewer joins, fewer tables to migrate,
--   atomic update semantics, and trivial backups.
--
--   What stays as its own table is anything with a lifecycle, a public-
--   facing identity, or cross-entity reporting (invoices, purchase
--   orders, transactions, reviews).
--
-- This file is rerun-safe: every CREATE / ALTER / INDEX uses IF NOT
-- EXISTS, and the consolidation block at the bottom only runs when the
-- legacy child tables are present.

-- ============================================================================
-- Core: Clients
-- Embeds:
--   tags         TEXT[]
--   addresses    JSONB array  [{id,label,line1,line2,city,state,pincode,primary}]
--   notes_log    JSONB array  [{id,body,category,pinned,author_email,created_at}]
-- ============================================================================

CREATE TABLE IF NOT EXISTS clients (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   VARCHAR(120) NOT NULL,
    email                  VARCHAR(255) NOT NULL,
    phone                  VARCHAR(20)  NOT NULL,
    source                 VARCHAR(40)  NOT NULL DEFAULT 'quote_request',
    status                 VARCHAR(40)  NOT NULL DEFAULT 'LEAD',
    notes                  TEXT,

    company_name           VARCHAR(160),
    lifecycle_stage        VARCHAR(40)  NOT NULL DEFAULT 'PROSPECT',
    referral_source        VARCHAR(80),
    lifetime_value_cents   BIGINT       NOT NULL DEFAULT 0,
    last_contacted_at      TIMESTAMPTZ,
    preferred_contact      VARCHAR(20),
    dietary_notes          TEXT,

    tags                   TEXT[]       NOT NULL DEFAULT '{}',
    addresses              JSONB        NOT NULL DEFAULT '[]'::jsonb,
    notes_log              JSONB        NOT NULL DEFAULT '[]'::jsonb,

    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

-- Idempotent column adds for existing dev DBs that started before this
-- consolidation. Safe no-op when the column already exists.
ALTER TABLE clients ADD COLUMN IF NOT EXISTS company_name           VARCHAR(160)--;;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS lifecycle_stage        VARCHAR(40)  NOT NULL DEFAULT 'PROSPECT'--;;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS referral_source        VARCHAR(80)--;;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS lifetime_value_cents   BIGINT       NOT NULL DEFAULT 0--;;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS last_contacted_at      TIMESTAMPTZ--;;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS preferred_contact      VARCHAR(20)--;;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS dietary_notes          TEXT--;;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS tags                   TEXT[]       NOT NULL DEFAULT '{}'--;;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS addresses              JSONB        NOT NULL DEFAULT '[]'::jsonb--;;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS notes_log              JSONB        NOT NULL DEFAULT '[]'::jsonb--;;

CREATE INDEX IF NOT EXISTS idx_clients_email      ON clients(email)--;;
CREATE INDEX IF NOT EXISTS idx_clients_status     ON clients(status)--;;
CREATE INDEX IF NOT EXISTS idx_clients_lifecycle  ON clients(lifecycle_stage)--;;
CREATE INDEX IF NOT EXISTS idx_clients_tags_gin   ON clients USING GIN (tags)--;;

-- ============================================================================
-- Core: Quote Requests
-- ============================================================================

CREATE TABLE IF NOT EXISTS quote_requests (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id    UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    event_type   VARCHAR(40)  NOT NULL,
    event_date   DATE         NOT NULL,
    guests       INTEGER      NOT NULL CHECK (guests > 0),
    venue        VARCHAR(200),
    budget       VARCHAR(40),
    message      TEXT,
    status       VARCHAR(40)  NOT NULL DEFAULT 'PENDING',
    responded_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

CREATE INDEX IF NOT EXISTS idx_quotes_client  ON quote_requests(client_id)--;;
CREATE INDEX IF NOT EXISTS idx_quotes_status  ON quote_requests(status)--;;
CREATE INDEX IF NOT EXISTS idx_quotes_date    ON quote_requests(event_date)--;;

-- ============================================================================
-- Core: Reviews (unified invitation + submitted review)
-- ============================================================================

CREATE TABLE IF NOT EXISTS reviews (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id               UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    type                    VARCHAR(20) NOT NULL,
    event_type              VARCHAR(40) NOT NULL,
    event_date              DATE        NOT NULL,
    token                   VARCHAR(64) UNIQUE,
    expires_at              TIMESTAMPTZ,
    sent_at                 TIMESTAMPTZ,
    used_at                 TIMESTAMPTZ,
    invitation_id           UUID REFERENCES reviews(id),
    reviewer_name           VARCHAR(120),
    overall_rating          SMALLINT CHECK (overall_rating BETWEEN 1 AND 5),
    food_quality_rating     SMALLINT CHECK (food_quality_rating BETWEEN 1 AND 5),
    taste_rating            SMALLINT CHECK (taste_rating BETWEEN 1 AND 5),
    presentation_rating     SMALLINT CHECK (presentation_rating BETWEEN 1 AND 5),
    staff_behavior_rating   SMALLINT CHECK (staff_behavior_rating BETWEEN 1 AND 5),
    timeliness_rating       SMALLINT CHECK (timeliness_rating BETWEEN 1 AND 5),
    service_quality_rating  SMALLINT CHECK (service_quality_rating BETWEEN 1 AND 5),
    comments                TEXT,
    suggestions             TEXT,
    recommend               VARCHAR(10),
    status                  VARCHAR(20) DEFAULT 'PENDING',
    is_featured             BOOLEAN DEFAULT FALSE,
    is_public               BOOLEAN DEFAULT FALSE,
    moderated_at            TIMESTAMPTZ,
    submitted_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
)--;;

CREATE INDEX IF NOT EXISTS idx_reviews_token    ON reviews(token)      WHERE type = 'INVITATION'--;;
CREATE INDEX IF NOT EXISTS idx_reviews_expires  ON reviews(expires_at) WHERE type = 'INVITATION' AND used_at IS NULL--;;
CREATE INDEX IF NOT EXISTS idx_reviews_public   ON reviews(is_public, created_at DESC) WHERE type = 'REVIEW' AND is_public = TRUE--;;
CREATE INDEX IF NOT EXISTS idx_reviews_featured ON reviews(is_featured) WHERE type = 'REVIEW' AND is_featured = TRUE--;;
CREATE INDEX IF NOT EXISTS idx_reviews_status   ON reviews(status)      WHERE type = 'REVIEW'--;;

-- ============================================================================
-- Core: Subscribers
-- ============================================================================

CREATE TABLE IF NOT EXISTS subscribers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    name            VARCHAR(120),
    source          VARCHAR(40) NOT NULL DEFAULT 'website',
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    unsubscribed_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
)--;;

CREATE INDEX IF NOT EXISTS idx_subscribers_active ON subscribers(is_active) WHERE is_active = TRUE--;;

-- ============================================================================
-- Email: Templates + Campaigns
-- ============================================================================

CREATE TABLE IF NOT EXISTS email_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) UNIQUE NOT NULL,
    code        VARCHAR(60),
    type        VARCHAR(40)  NOT NULL,
    subject     VARCHAR(200) NOT NULL,
    preheader   VARCHAR(200),
    content     JSONB        NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

ALTER TABLE email_templates ADD COLUMN IF NOT EXISTS code VARCHAR(60)--;;

CREATE INDEX IF NOT EXISTS idx_templates_type ON email_templates(type) WHERE is_active = TRUE--;;
CREATE UNIQUE INDEX IF NOT EXISTS idx_templates_code_lower
    ON email_templates (LOWER(code))
    WHERE code IS NOT NULL--;;

CREATE TABLE IF NOT EXISTS email_campaigns (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    recipients          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    total_recipients    INTEGER      NOT NULL DEFAULT 0,
    sent_count          INTEGER      NOT NULL DEFAULT 0,
    failed_count        INTEGER      NOT NULL DEFAULT 0,
    config              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    global_variables    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    default_template_id UUID REFERENCES email_templates(id),
    scheduled_at        TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    locked_at           TIMESTAMPTZ,
    locked_by           VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

ALTER TABLE email_campaigns ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ--;;
ALTER TABLE email_campaigns ADD COLUMN IF NOT EXISTS locked_by VARCHAR(64)--;;

CREATE INDEX IF NOT EXISTS idx_campaigns_status   ON email_campaigns(status)--;;
CREATE INDEX IF NOT EXISTS idx_campaigns_schedule ON email_campaigns(scheduled_at) WHERE status = 'QUEUED'--;;
CREATE INDEX IF NOT EXISTS idx_campaigns_created  ON email_campaigns(created_at DESC)--;;
CREATE INDEX IF NOT EXISTS idx_campaigns_lock     ON email_campaigns(status, locked_at, scheduled_at)--;;

-- ============================================================================
-- System logs
-- ============================================================================

CREATE TABLE IF NOT EXISTS system_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type         VARCHAR(40) NOT NULL,
    entity_type  VARCHAR(40),
    entity_id    UUID,
    action       VARCHAR(60) NOT NULL,
    status       VARCHAR(20),
    details      JSONB,
    ip_address   INET,
    user_agent   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
)--;;

CREATE INDEX IF NOT EXISTS idx_logs_type    ON system_logs(type)--;;
CREATE INDEX IF NOT EXISTS idx_logs_entity  ON system_logs(entity_type, entity_id)--;;
CREATE INDEX IF NOT EXISTS idx_logs_created ON system_logs(created_at DESC)--;;

-- ============================================================================
-- Bookings
-- Embeds:
--   tasks  JSONB array  [{id,title,description,due_at,assignee,status,position,completed_at,created_at}]
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS booking_reference_seq START 1--;;

CREATE TABLE IF NOT EXISTS bookings (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference             VARCHAR(24)  UNIQUE NOT NULL,
    client_id             UUID         NOT NULL REFERENCES clients(id)        ON DELETE RESTRICT,
    quote_request_id      UUID         REFERENCES quote_requests(id)          ON DELETE SET NULL,

    event_type            VARCHAR(40)  NOT NULL,
    event_date            DATE         NOT NULL,
    event_start_time      TIME,
    event_end_time        TIME,
    guest_count           INTEGER      NOT NULL CHECK (guest_count > 0),

    venue_name            VARCHAR(200),
    venue_address         TEXT,

    service_style         VARCHAR(40),
    package_name          VARCHAR(160),

    status                VARCHAR(40)  NOT NULL DEFAULT 'CONFIRMED',

    -- Money rolled up by services so list views never recompute.
    total_amount_cents      BIGINT     NOT NULL DEFAULT 0,
    deposit_amount_cents    BIGINT     NOT NULL DEFAULT 0,
    paid_amount_cents       BIGINT     NOT NULL DEFAULT 0,
    invoiced_amount_cents   BIGINT     NOT NULL DEFAULT 0,
    direct_expense_cents    BIGINT     NOT NULL DEFAULT 0,
    currency                VARCHAR(3) NOT NULL DEFAULT 'INR',

    internal_notes        TEXT,
    client_notes          TEXT,
    menu_summary          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    staffing              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    tasks                 JSONB        NOT NULL DEFAULT '[]'::jsonb,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS invoiced_amount_cents BIGINT NOT NULL DEFAULT 0--;;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS direct_expense_cents  BIGINT NOT NULL DEFAULT 0--;;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS tasks                 JSONB  NOT NULL DEFAULT '[]'::jsonb--;;

CREATE INDEX IF NOT EXISTS idx_bookings_client      ON bookings(client_id)--;;
CREATE INDEX IF NOT EXISTS idx_bookings_quote       ON bookings(quote_request_id)--;;
CREATE INDEX IF NOT EXISTS idx_bookings_status      ON bookings(status)--;;
CREATE INDEX IF NOT EXISTS idx_bookings_event_date  ON bookings(event_date)--;;
CREATE INDEX IF NOT EXISTS idx_bookings_created     ON bookings(created_at DESC)--;;

-- ============================================================================
-- Vendors
-- Embeds:
--   tags        TEXT[]
--   contacts    JSONB array  [{id,name,role,phone,email,primary,notes}]
--   rate_cards  JSONB array  [{id,item_name,unit,unit_price_cents,currency,valid_from,valid_to,active,notes}]
-- ============================================================================

CREATE TABLE IF NOT EXISTS vendors (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   VARCHAR(160) NOT NULL,
    category               VARCHAR(40)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    primary_contact_name   VARCHAR(120),
    primary_contact_phone  VARCHAR(20),
    primary_contact_email  VARCHAR(255),

    gst_number             VARCHAR(32),
    pan_number             VARCHAR(20),
    website                VARCHAR(255),

    address_line1          VARCHAR(200),
    address_line2          VARCHAR(200),
    city                   VARCHAR(80),
    state                  VARCHAR(80),
    pincode                VARCHAR(20),

    payment_terms          VARCHAR(40)  NOT NULL DEFAULT 'NET_30',
    preferred_payment      VARCHAR(40),
    currency               VARCHAR(3)   NOT NULL DEFAULT 'INR',

    rating                 NUMERIC(3,2) CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5)),
    rating_count           INTEGER      NOT NULL DEFAULT 0,
    total_spend_cents      BIGINT       NOT NULL DEFAULT 0,
    last_ordered_at        TIMESTAMPTZ,

    notes                  TEXT,

    tags                   TEXT[]       NOT NULL DEFAULT '{}',
    contacts               JSONB        NOT NULL DEFAULT '[]'::jsonb,
    rate_cards             JSONB        NOT NULL DEFAULT '[]'::jsonb,

    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

ALTER TABLE vendors ADD COLUMN IF NOT EXISTS tags        TEXT[] NOT NULL DEFAULT '{}'--;;
ALTER TABLE vendors ADD COLUMN IF NOT EXISTS contacts    JSONB  NOT NULL DEFAULT '[]'::jsonb--;;
ALTER TABLE vendors ADD COLUMN IF NOT EXISTS rate_cards  JSONB  NOT NULL DEFAULT '[]'::jsonb--;;

CREATE INDEX IF NOT EXISTS idx_vendors_category   ON vendors(category)--;;
CREATE INDEX IF NOT EXISTS idx_vendors_status     ON vendors(status)--;;
CREATE INDEX IF NOT EXISTS idx_vendors_name_lower ON vendors(LOWER(name))--;;
CREATE INDEX IF NOT EXISTS idx_vendors_tags_gin   ON vendors USING GIN (tags)--;;
CREATE UNIQUE INDEX IF NOT EXISTS idx_vendors_unique_name_per_category
    ON vendors(category, LOWER(name))--;;

-- ============================================================================
-- Purchase Orders
-- Embeds:
--   items  JSONB array  [{id,description,quantity,unit,unit_price_cents,line_total_cents,position,rate_card_id,notes}]
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS po_reference_seq START 1--;;

CREATE TABLE IF NOT EXISTS purchase_orders (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference             VARCHAR(24)  UNIQUE NOT NULL,

    vendor_id             UUID         NOT NULL REFERENCES vendors(id)  ON DELETE RESTRICT,
    booking_id            UUID         REFERENCES bookings(id)          ON DELETE SET NULL,

    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',

    issue_date            DATE,
    expected_delivery     DATE,
    received_at           TIMESTAMPTZ,

    subtotal_cents        BIGINT       NOT NULL DEFAULT 0,
    tax_cents             BIGINT       NOT NULL DEFAULT 0,
    total_cents           BIGINT       NOT NULL DEFAULT 0,
    paid_cents            BIGINT       NOT NULL DEFAULT 0,
    currency              VARCHAR(3)   NOT NULL DEFAULT 'INR',

    internal_notes        TEXT,
    vendor_notes          TEXT,

    items                 JSONB        NOT NULL DEFAULT '[]'::jsonb,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS items JSONB NOT NULL DEFAULT '[]'::jsonb--;;

CREATE INDEX IF NOT EXISTS idx_pos_vendor    ON purchase_orders(vendor_id)--;;
CREATE INDEX IF NOT EXISTS idx_pos_booking   ON purchase_orders(booking_id)--;;
CREATE INDEX IF NOT EXISTS idx_pos_status    ON purchase_orders(status)--;;
CREATE INDEX IF NOT EXISTS idx_pos_issue     ON purchase_orders(issue_date DESC NULLS LAST)--;;

-- ============================================================================
-- Invoices (Phase 3)
-- Embeds:
--   items  JSONB array  [{id,description,quantity,unit,unit_price_cents,line_total_cents,position,notes}]
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS invoice_reference_seq START 1--;;

CREATE TABLE IF NOT EXISTS invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference       VARCHAR(24)  UNIQUE NOT NULL,

    booking_id      UUID         NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
    -- Denormalised so the list view doesn't need to join through bookings.
    client_id       UUID         NOT NULL REFERENCES clients(id)  ON DELETE RESTRICT,

    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',

    issue_date      DATE,
    due_date        DATE,
    sent_at         TIMESTAMPTZ,
    voided_at       TIMESTAMPTZ,

    subtotal_cents  BIGINT       NOT NULL DEFAULT 0,
    tax_cents       BIGINT       NOT NULL DEFAULT 0,
    discount_cents  BIGINT       NOT NULL DEFAULT 0,
    total_cents     BIGINT       NOT NULL DEFAULT 0,
    paid_cents      BIGINT       NOT NULL DEFAULT 0,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'INR',

    terms           TEXT,
    notes           TEXT,

    items           JSONB        NOT NULL DEFAULT '[]'::jsonb,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

CREATE INDEX IF NOT EXISTS idx_invoices_booking  ON invoices(booking_id)--;;
CREATE INDEX IF NOT EXISTS idx_invoices_client   ON invoices(client_id)--;;
CREATE INDEX IF NOT EXISTS idx_invoices_status   ON invoices(status)--;;
CREATE INDEX IF NOT EXISTS idx_invoices_due_date ON invoices(due_date)--;;
CREATE INDEX IF NOT EXISTS idx_invoices_unpaid
    ON invoices(status) WHERE status IN ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')--;;

-- ============================================================================
-- Transactions (Phase 3, unified payments + expenses)
--
-- A single ledger of every cash movement, distinguished by `direction`:
--   INCOMING — client paid us (a Payment in old terminology)
--   OUTGOING — we paid someone (Expense in old terminology, incl. PO settlements)
--
-- Attribution columns are scoped to the direction:
--   INCOMING uses client_id, optional invoice_id, optional booking_id.
--   OUTGOING uses category, optional vendor_id, optional purchase_order_id,
--     optional booking_id (when the spend is event-attributable).
--
-- This single table replaces what would have been two tables (payments +
-- expenses). Cash-flow and P&L queries become trivial — `SELECT direction,
-- SUM(amount_cents)` instead of UNIONing two ledgers.
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS transaction_reference_seq START 1--;;

CREATE TABLE IF NOT EXISTS transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference           VARCHAR(24)  UNIQUE NOT NULL,

    direction           VARCHAR(10)  NOT NULL CHECK (direction IN ('INCOMING', 'OUTGOING')),

    amount_cents        BIGINT       NOT NULL CHECK (amount_cents >= 0),
    currency            VARCHAR(3)   NOT NULL DEFAULT 'INR',

    method              VARCHAR(20)  NOT NULL DEFAULT 'CASH',
    paid_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    transaction_ref     VARCHAR(120),

    status              VARCHAR(20)  NOT NULL DEFAULT 'RECORDED',

    -- Required for OUTGOING--;; null for INCOMING.
    category            VARCHAR(40),

    -- Attribution (subset applies to each direction--;; see comments above).
    client_id           UUID         REFERENCES clients(id)         ON DELETE SET NULL,
    invoice_id          UUID         REFERENCES invoices(id)        ON DELETE SET NULL,
    vendor_id           UUID         REFERENCES vendors(id)         ON DELETE SET NULL,
    purchase_order_id   UUID         REFERENCES purchase_orders(id) ON DELETE SET NULL,
    booking_id          UUID         REFERENCES bookings(id)        ON DELETE SET NULL,

    description         VARCHAR(300),
    notes               TEXT,
    receipt_url         VARCHAR(500),

    -- Refund info (INCOMING only).
    refunded_at         TIMESTAMPTZ,
    refund_reason       TEXT,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

CREATE INDEX IF NOT EXISTS idx_tx_direction   ON transactions(direction)--;;
CREATE INDEX IF NOT EXISTS idx_tx_paid_at     ON transactions(paid_at DESC)--;;
CREATE INDEX IF NOT EXISTS idx_tx_booking     ON transactions(booking_id)--;;
CREATE INDEX IF NOT EXISTS idx_tx_invoice     ON transactions(invoice_id)--;;
CREATE INDEX IF NOT EXISTS idx_tx_vendor      ON transactions(vendor_id)--;;
CREATE INDEX IF NOT EXISTS idx_tx_po          ON transactions(purchase_order_id)--;;
CREATE INDEX IF NOT EXISTS idx_tx_client      ON transactions(client_id)--;;
CREATE INDEX IF NOT EXISTS idx_tx_category    ON transactions(category)  WHERE direction = 'OUTGOING'--;;
CREATE INDEX IF NOT EXISTS idx_tx_active
    ON transactions(direction, paid_at DESC) WHERE status <> 'REFUNDED'--;;

-- ============================================================================
-- Comments
-- ============================================================================

COMMENT ON TABLE clients          IS 'Customers--;; embeds tags TEXT[], addresses JSONB, notes_log JSONB'--;;
COMMENT ON TABLE quote_requests   IS 'Quote requests submitted by clients'--;;
COMMENT ON TABLE bookings         IS 'Confirmed events--;; embeds tasks JSONB'--;;
COMMENT ON TABLE vendors          IS 'Suppliers--;; embeds tags TEXT[], contacts JSONB, rate_cards JSONB'--;;
COMMENT ON TABLE purchase_orders  IS 'Orders to vendors, optionally tagged to a booking--;; embeds items JSONB'--;;
COMMENT ON TABLE invoices         IS 'Formal billing artefacts per booking--;; embeds items JSONB'--;;
COMMENT ON TABLE transactions     IS 'Unified cash-flow ledger (incoming = payments, outgoing = expenses)'--;;
COMMENT ON TABLE reviews          IS 'Review invitations and submitted reviews (unified)'--;;
COMMENT ON TABLE subscribers      IS 'Newsletter subscribers'--;;
COMMENT ON TABLE email_templates  IS 'Email templates with JSON content'--;;
COMMENT ON TABLE email_campaigns  IS 'Email campaign tracking and history'--;;
COMMENT ON TABLE system_logs      IS 'Audit logs for all system actions'--;;

-- ============================================================================
-- One-shot consolidation: copy data from legacy child tables into JSONB
-- columns on the parent, then drop the child tables.
--
-- This block is rerun-safe — every step is guarded by a check for the
-- legacy table's existence. Once the database has been consolidated the
-- guards short-circuit.
-- ============================================================================

-- Two-phase migration: copy first, drop second.
--
-- Splitting them avoids FK-dependency hazards (e.g. purchase_order_items
-- has an FK to vendor_rate_cards, so dropping vendor_rate_cards first
-- would fail). The DROP phase uses CASCADE on each legacy table so
-- residual constraints can't block the rerun-safe migration.

DO $$
BEGIN
    /* ────────────── Phase A: copy data into JSONB / TEXT[] ────────────── */

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'client_addresses') THEN
        UPDATE clients c SET addresses = COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'id',         a.id,
                    'label',      a.label,
                    'line1',      a.line1,
                    'line2',      a.line2,
                    'city',       a.city,
                    'state',      a.state,
                    'pincode',    a.pincode,
                    'primary',    a.is_primary,
                    'createdAt',  a.created_at
                ) ORDER BY a.is_primary DESC, a.created_at ASC
            )
            FROM client_addresses a WHERE a.client_id = c.id
        ), '[]'::jsonb)
        WHERE EXISTS (SELECT 1 FROM client_addresses a WHERE a.client_id = c.id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'client_notes') THEN
        UPDATE clients c SET notes_log = COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'id',          n.id,
                    'body',        n.body,
                    'category',    n.category,
                    'pinned',      n.pinned,
                    'authorEmail', n.author_email,
                    'createdAt',   n.created_at,
                    'updatedAt',   n.updated_at
                ) ORDER BY n.pinned DESC, n.created_at DESC
            )
            FROM client_notes n WHERE n.client_id = c.id
        ), '[]'::jsonb)
        WHERE EXISTS (SELECT 1 FROM client_notes n WHERE n.client_id = c.id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'client_tags')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'tags') THEN
        UPDATE clients c SET tags = COALESCE((
            SELECT array_agg(DISTINCT t.name)
            FROM client_tags ct
            JOIN tags t ON t.id = ct.tag_id
            WHERE ct.client_id = c.id
        ), '{}')
        WHERE EXISTS (SELECT 1 FROM client_tags ct WHERE ct.client_id = c.id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vendor_tags')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'tags') THEN
        UPDATE vendors v SET tags = COALESCE((
            SELECT array_agg(DISTINCT t.name)
            FROM vendor_tags vt
            JOIN tags t ON t.id = vt.tag_id
            WHERE vt.vendor_id = v.id
        ), '{}')
        WHERE EXISTS (SELECT 1 FROM vendor_tags vt WHERE vt.vendor_id = v.id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vendor_contacts') THEN
        UPDATE vendors v SET contacts = COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'id',         vc.id,
                    'name',       vc.name,
                    'role',       vc.role,
                    'phone',      vc.phone,
                    'email',      vc.email,
                    'primary',    vc.is_primary,
                    'notes',      vc.notes,
                    'createdAt',  vc.created_at
                ) ORDER BY vc.is_primary DESC, vc.created_at ASC
            )
            FROM vendor_contacts vc WHERE vc.vendor_id = v.id
        ), '[]'::jsonb)
        WHERE EXISTS (SELECT 1 FROM vendor_contacts vc WHERE vc.vendor_id = v.id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vendor_rate_cards') THEN
        UPDATE vendors v SET rate_cards = COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'id',              r.id,
                    'itemName',        r.item_name,
                    'unit',            r.unit,
                    'unitPriceCents',  r.unit_price_cents,
                    'currency',        r.currency,
                    'validFrom',       r.valid_from,
                    'validTo',         r.valid_to,
                    'active',          r.is_active,
                    'notes',           r.notes
                ) ORDER BY r.is_active DESC, lower(r.item_name) ASC
            )
            FROM vendor_rate_cards r WHERE r.vendor_id = v.id
        ), '[]'::jsonb)
        WHERE EXISTS (SELECT 1 FROM vendor_rate_cards r WHERE r.vendor_id = v.id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'booking_tasks') THEN
        UPDATE bookings b SET tasks = COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'id',           bt.id,
                    'title',        bt.title,
                    'description',  bt.description,
                    'dueAt',        bt.due_at,
                    'assignee',     bt.assignee,
                    'status',       lower(bt.status),
                    'position',     bt.position,
                    'completedAt',  bt.completed_at,
                    'createdAt',    bt.created_at
                ) ORDER BY bt.position, bt.created_at
            )
            FROM booking_tasks bt WHERE bt.booking_id = b.id
        ), '[]'::jsonb)
        WHERE EXISTS (SELECT 1 FROM booking_tasks bt WHERE bt.booking_id = b.id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'purchase_order_items') THEN
        UPDATE purchase_orders p SET items = COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'id',              pi.id,
                    'description',     pi.description,
                    'quantity',        pi.quantity,
                    'unit',            pi.unit,
                    'unitPriceCents',  pi.unit_price_cents,
                    'lineTotalCents',  pi.line_total_cents,
                    'position',        pi.position,
                    'rateCardId',      pi.rate_card_id,
                    'notes',           pi.notes
                ) ORDER BY pi.position
            )
            FROM purchase_order_items pi WHERE pi.purchase_order_id = p.id
        ), '[]'::jsonb)
        WHERE EXISTS (SELECT 1 FROM purchase_order_items pi WHERE pi.purchase_order_id = p.id);
    END IF;

    /* ────────────── Phase B: drop legacy tables (CASCADE) ────────────── */
    -- CASCADE so any residual cross-table FKs (e.g. purchase_order_items
    -- → vendor_rate_cards) don't block the drop. The dependent tables are
    -- all in this drop set anyway.

    DROP TABLE IF EXISTS purchase_order_items CASCADE;
    DROP TABLE IF EXISTS booking_tasks        CASCADE;
    DROP TABLE IF EXISTS vendor_rate_cards    CASCADE;
    DROP TABLE IF EXISTS vendor_contacts      CASCADE;
    DROP TABLE IF EXISTS vendor_tags          CASCADE;
    DROP TABLE IF EXISTS client_tags          CASCADE;
    DROP TABLE IF EXISTS client_notes         CASCADE;
    DROP TABLE IF EXISTS client_addresses     CASCADE;
    DROP TABLE IF EXISTS tags                 CASCADE;
END$$--;;

-- ============================================================================
-- Backfill + harden collection columns
--
-- Some dev databases were created when Hibernate's ddl-auto introduced these
-- columns as plain nullable types (no DEFAULT, no NOT NULL) before this
-- schema.sql could enforce them via ALTER ADD COLUMN IF NOT EXISTS. We
-- coalesce any lingering NULLs to empty collections, then promote the
-- columns to NOT NULL with a sensible default. Idempotent and cheap.
-- ============================================================================

UPDATE clients          SET tags       = '{}'           WHERE tags       IS NULL--;;
UPDATE clients          SET addresses  = '[]'::jsonb    WHERE addresses  IS NULL--;;
UPDATE clients          SET notes_log  = '[]'::jsonb    WHERE notes_log  IS NULL--;;
ALTER TABLE clients     ALTER COLUMN tags      SET DEFAULT '{}',          ALTER COLUMN tags      SET NOT NULL--;;
ALTER TABLE clients     ALTER COLUMN addresses SET DEFAULT '[]'::jsonb,   ALTER COLUMN addresses SET NOT NULL--;;
ALTER TABLE clients     ALTER COLUMN notes_log SET DEFAULT '[]'::jsonb,   ALTER COLUMN notes_log SET NOT NULL--;;

UPDATE vendors          SET tags       = '{}'           WHERE tags       IS NULL--;;
UPDATE vendors          SET contacts   = '[]'::jsonb    WHERE contacts   IS NULL--;;
UPDATE vendors          SET rate_cards = '[]'::jsonb    WHERE rate_cards IS NULL--;;
ALTER TABLE vendors     ALTER COLUMN tags       SET DEFAULT '{}',         ALTER COLUMN tags       SET NOT NULL--;;
ALTER TABLE vendors     ALTER COLUMN contacts   SET DEFAULT '[]'::jsonb,  ALTER COLUMN contacts   SET NOT NULL--;;
ALTER TABLE vendors     ALTER COLUMN rate_cards SET DEFAULT '[]'::jsonb,  ALTER COLUMN rate_cards SET NOT NULL--;;

UPDATE bookings         SET tasks         = '[]'::jsonb WHERE tasks         IS NULL--;;
UPDATE bookings         SET menu_summary  = '{}'::jsonb WHERE menu_summary  IS NULL--;;
UPDATE bookings         SET staffing      = '{}'::jsonb WHERE staffing      IS NULL--;;
ALTER TABLE bookings    ALTER COLUMN tasks        SET DEFAULT '[]'::jsonb, ALTER COLUMN tasks        SET NOT NULL--;;
ALTER TABLE bookings    ALTER COLUMN menu_summary SET DEFAULT '{}'::jsonb, ALTER COLUMN menu_summary SET NOT NULL--;;
ALTER TABLE bookings    ALTER COLUMN staffing     SET DEFAULT '{}'::jsonb, ALTER COLUMN staffing     SET NOT NULL--;;

UPDATE purchase_orders  SET items = '[]'::jsonb WHERE items IS NULL--;;
ALTER TABLE purchase_orders ALTER COLUMN items SET DEFAULT '[]'::jsonb, ALTER COLUMN items SET NOT NULL--;;

UPDATE invoices         SET items = '[]'::jsonb WHERE items IS NULL--;;
ALTER TABLE invoices    ALTER COLUMN items SET DEFAULT '[]'::jsonb, ALTER COLUMN items SET NOT NULL--;;

-- ============================================================================
-- Document Studio: Branding Profile (singleton)
--
-- A single row holds every brand setting used across generated documents
-- (letterheads, menus, invoices, POs, proposals). Singleton enforced at the
-- service layer (getOrCreate) — exposing CRUD here would invite drift across
-- multiple rows. Asset URLs (logos, QR codes, signatures) land in slice 3
-- when the asset uploader exists; until then the relevant columns stay
-- nullable so the form can save partial state without lying.
-- ============================================================================

CREATE TABLE IF NOT EXISTS branding_profiles (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    brand_name         VARCHAR(160),
    tagline            VARCHAR(200),
    established_year   INTEGER,

    phone_primary      VARCHAR(20),
    phone_secondary    VARCHAR(20),
    email              VARCHAR(255),
    website            VARCHAR(255),

    address_line1      VARCHAR(200),
    address_line2      VARCHAR(200),
    city               VARCHAR(80),
    state              VARCHAR(80),
    pincode            VARCHAR(20),

    primary_color      VARCHAR(16),
    secondary_color    VARCHAR(16),
    accent_color       VARCHAR(16),
    ink_color          VARCHAR(16),

    display_font       VARCHAR(120),
    body_font          VARCHAR(120),

    gstin              VARCHAR(32),
    fssai_license      VARCHAR(40),
    cin                VARCHAR(40),
    pan_number         VARCHAR(20),

    brand_promise      TEXT,
    legal_disclaimer   TEXT,

    social_instagram   VARCHAR(255),
    social_facebook    VARCHAR(255),
    social_youtube     VARCHAR(255),

    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
)--;;

CREATE INDEX IF NOT EXISTS idx_branding_profiles_updated ON branding_profiles(updated_at DESC)--;;

-- ============================================================================
-- Document Studio: Brand Assets
--
-- Uploaded image files (logos, monogram, watermark, signatures, QR codes,
-- decorative motifs). The binary lives on local disk under
-- studio.assets.dir; this table only records metadata + the public URL.
-- One row per uploaded file.
--
-- "role" is a soft tag — many assets can share the same role and the
-- branding profile picks one of them. Free-form on purpose so admins can
-- add roles we didn't anticipate (e.g. SEASONAL_BANNER, FESTIVAL_MOTIF)
-- without a schema migration.
-- ============================================================================

CREATE TABLE IF NOT EXISTS brand_assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename        VARCHAR(200) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    content_type    VARCHAR(80)  NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    role            VARCHAR(40)  NOT NULL DEFAULT 'DECORATIVE',
    alt_text        VARCHAR(255),
    public_url      VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

CREATE INDEX IF NOT EXISTS idx_brand_assets_role    ON brand_assets(role)--;;
CREATE INDEX IF NOT EXISTS idx_brand_assets_created ON brand_assets(created_at DESC)--;;

-- ============================================================================
-- Document Studio: Generated Documents log
--
-- One row per rendered PDF (letterhead, menu, invoice, proposal, PO). Drives
-- the Print Center's "Recent documents" surface and gives us re-download
-- without re-rendering. The PDF bytes themselves are NOT stored here —
-- they live on disk under studio.assets.dir/documents/, addressed by
-- `pdf_filename`. Keeps the row small and lets the same backup strategy
-- as brand_assets cover both.
-- ============================================================================

CREATE TABLE IF NOT EXISTS generated_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type     VARCHAR(40)  NOT NULL,   -- letterhead, menu, invoice, proposal, po, welcome_note
    document_number   VARCHAR(60),             -- human-readable id; null for ad-hoc renders
    pdf_filename      VARCHAR(200) NOT NULL,
    pdf_url           VARCHAR(500) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    template_version  VARCHAR(40)  NOT NULL DEFAULT 'v1',
    rendered_by_email VARCHAR(255),
    branding_id       UUID         REFERENCES branding_profiles(id) ON DELETE SET NULL,
    booking_id        UUID,                    -- optional link; bookings table FK omitted to keep this module decoupled
    client_id         UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
)--;;

CREATE INDEX IF NOT EXISTS idx_generated_documents_type    ON generated_documents(document_type)--;;
CREATE INDEX IF NOT EXISTS idx_generated_documents_created ON generated_documents(created_at DESC)--;;
