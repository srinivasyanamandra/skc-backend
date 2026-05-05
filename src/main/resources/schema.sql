-- Sri Karthikeya Caterers Database Schema
-- PostgreSQL 14+

-- Table 1: Clients
CREATE TABLE IF NOT EXISTS clients (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    source      VARCHAR(40)  NOT NULL DEFAULT 'quote_request',
    status      VARCHAR(40)  NOT NULL DEFAULT 'LEAD',
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_clients_email  ON clients(email);
CREATE INDEX IF NOT EXISTS idx_clients_status ON clients(status);

-- Table 2: Quote Requests
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
);

CREATE INDEX IF NOT EXISTS idx_quotes_client  ON quote_requests(client_id);
CREATE INDEX IF NOT EXISTS idx_quotes_status  ON quote_requests(status);
CREATE INDEX IF NOT EXISTS idx_quotes_date    ON quote_requests(event_date);

-- Table 3: Reviews (unified invitation + submitted)
CREATE TABLE IF NOT EXISTS reviews (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id               UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    type                    VARCHAR(20) NOT NULL,
    event_type              VARCHAR(40) NOT NULL,
    event_date              DATE        NOT NULL,
    
    -- Invitation columns
    token                   VARCHAR(64) UNIQUE,
    expires_at              TIMESTAMPTZ,
    sent_at                 TIMESTAMPTZ,
    used_at                 TIMESTAMPTZ,
    
    -- Submitted review columns
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
);

CREATE INDEX IF NOT EXISTS idx_reviews_token    ON reviews(token)      WHERE type = 'INVITATION';
CREATE INDEX IF NOT EXISTS idx_reviews_expires  ON reviews(expires_at) WHERE type = 'INVITATION' AND used_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_reviews_public   ON reviews(is_public, created_at DESC) WHERE type = 'REVIEW' AND is_public = TRUE;
CREATE INDEX IF NOT EXISTS idx_reviews_featured ON reviews(is_featured) WHERE type = 'REVIEW' AND is_featured = TRUE;
CREATE INDEX IF NOT EXISTS idx_reviews_status   ON reviews(status)      WHERE type = 'REVIEW';

-- Table 4: Subscribers
CREATE TABLE IF NOT EXISTS subscribers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    name            VARCHAR(120),
    source          VARCHAR(40) NOT NULL DEFAULT 'website',
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    unsubscribed_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_subscribers_active ON subscribers(is_active) WHERE is_active = TRUE;

-- Table 5: Email Templates
CREATE TABLE IF NOT EXISTS email_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) UNIQUE NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    subject     VARCHAR(200) NOT NULL,
    preheader   VARCHAR(200),
    content     JSONB        NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_templates_type ON email_templates(type) WHERE is_active = TRUE;

-- Table 6: Email Campaigns
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
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_campaigns_status   ON email_campaigns(status);
CREATE INDEX IF NOT EXISTS idx_campaigns_schedule ON email_campaigns(scheduled_at) WHERE status = 'QUEUED';
CREATE INDEX IF NOT EXISTS idx_campaigns_created  ON email_campaigns(created_at DESC);

-- Table 7: System Logs
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
);

CREATE INDEX IF NOT EXISTS idx_logs_type    ON system_logs(type);
CREATE INDEX IF NOT EXISTS idx_logs_entity  ON system_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_logs_created ON system_logs(created_at DESC);

-- Comments
COMMENT ON TABLE clients IS 'Customer information from quote requests';
COMMENT ON TABLE quote_requests IS 'Quote requests submitted by clients';
COMMENT ON TABLE reviews IS 'Review invitations and submitted reviews (unified table)';
COMMENT ON TABLE subscribers IS 'Newsletter subscribers';
COMMENT ON TABLE email_templates IS 'Email templates with JSON content';
COMMENT ON TABLE email_campaigns IS 'Email campaign tracking and history';
COMMENT ON TABLE system_logs IS 'Audit logs for all system actions';
