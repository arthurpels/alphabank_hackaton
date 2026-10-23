CREATE TABLE company (
    id             uuid PRIMARY KEY,
    inn            varchar(12) NOT NULL UNIQUE,
    name           text        NOT NULL,
    legal_form     varchar(3)  NOT NULL,
    egrul_snapshot jsonb,
    CONSTRAINT chk_company_inn CHECK (inn ~ '^(\d{10}|\d{12})$'),
    CONSTRAINT chk_company_legal_form CHECK (legal_form IN ('OOO', 'IP'))
);

CREATE TABLE app_user (
    id         uuid PRIMARY KEY,
    company_id uuid REFERENCES company (id),
    role       varchar(12) NOT NULL,
    fio        text        NOT NULL,
    CONSTRAINT chk_user_role CHECK (role IN ('CLIENT', 'UNDERWRITER'))
);

CREATE TABLE product (
    id            uuid PRIMARY KEY,
    code          varchar(20) NOT NULL UNIQUE,
    name          text        NOT NULL,
    min_amount    bigint      NOT NULL,
    max_amount    bigint      NOT NULL,
    allowed_terms jsonb       NOT NULL,
    CONSTRAINT chk_product_amounts CHECK (min_amount > 0 AND max_amount >= min_amount)
);

CREATE TABLE application (
    id                 uuid PRIMARY KEY,
    company_id         uuid NOT NULL REFERENCES company (id),
    user_id            uuid NOT NULL REFERENCES app_user (id),
    product_id         uuid NOT NULL REFERENCES product (id),
    amount             bigint      NOT NULL,
    term_months        integer     NOT NULL,
    goal               text        NOT NULL,
    status             varchar(15) NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    submitted_at       timestamptz,
    info_requested_at  timestamptz,
    CONSTRAINT chk_application_amount CHECK (amount > 0),
    CONSTRAINT chk_application_term CHECK (term_months > 0),
    CONSTRAINT chk_application_status CHECK (status IN
        ('DRAFT', 'COLLECTING', 'SUBMITTED', 'AUTO_CHECK', 'UNDERWRITING',
         'INFO_REQUESTED', 'APPROVED', 'COUNTER_OFFER', 'REJECTED',
         'SIGNING', 'ISSUED', 'CANCELLED'))
);

CREATE TABLE spot (
    id              uuid PRIMARY KEY,
    application_id  uuid NOT NULL REFERENCES application (id),
    type            varchar(12) NOT NULL,
    title           text        NOT NULL,
    client_hint     text        NOT NULL,
    scoring_factor  text,
    price_pp        numeric(5, 2),
    close_method    varchar(15) NOT NULL,
    status          varchar(9)  NOT NULL,
    added_by        varchar(11) NOT NULL,
    source          varchar(12),
    created_at      timestamptz NOT NULL DEFAULT now(),
    closed_at       timestamptz,
    CONSTRAINT chk_spot_type CHECK (type IN
        ('NO_DATA', 'ANOMALY', 'DOCUMENT', 'STOP_FACTOR', 'COLLATERAL')),
    CONSTRAINT chk_spot_close_method CHECK (close_method IN
        ('CONNECT_SOURCE', 'ANSWER', 'UPLOAD', 'PAY', 'ADD_COLLATERAL')),
    CONSTRAINT chk_spot_status CHECK (status IN ('OPEN', 'ON_REVIEW', 'CLOSED')),
    CONSTRAINT chk_spot_added_by CHECK (added_by IN ('SYSTEM', 'UNDERWRITER')),
    CONSTRAINT chk_spot_source CHECK (source IS NULL OR source IN
        ('BKI', 'EGRUL', 'FNS', 'OFD', 'MARKETPLACE', 'ACQUIRING', 'REPUTATION')),
    CONSTRAINT chk_spot_price CHECK (price_pp IS NULL OR price_pp >= 0)
);

CREATE TABLE document (
    id             uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES application (id),
    kind           text        NOT NULL,
    file_path      text        NOT NULL,
    uploaded_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE spot_answer (
    id              uuid PRIMARY KEY,
    spot_id         uuid NOT NULL REFERENCES spot (id),
    text            text,
    document_id     uuid REFERENCES document (id),
    verdict         varchar(8) NOT NULL DEFAULT 'PENDING',
    reviewed_by     varchar(36),
    review_comment  text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    reviewed_at     timestamptz,
    CONSTRAINT chk_spot_answer_verdict CHECK (verdict IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE TABLE fork_snapshot (
    id             uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES application (id),
    rate_from      numeric(5, 2) NOT NULL,
    rate_to        numeric(5, 2) NOT NULL,
    rules_version  text          NOT NULL,
    calculated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT chk_fork_edges CHECK (rate_from > 0 AND rate_to >= rate_from)
);

CREATE TABLE offer (
    id                uuid PRIMARY KEY,
    application_id    uuid NOT NULL REFERENCES application (id),
    product_id        uuid NOT NULL REFERENCES product (id),
    amount            bigint      NOT NULL,
    term_months       integer     NOT NULL,
    collateral_type   text,
    collateral_comment text,
    fork_snapshot_id  uuid NOT NULL REFERENCES fork_snapshot (id),
    proposed_by       varchar(11) NOT NULL,
    status            varchar(10) NOT NULL,
    comment           text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    chosen_at         timestamptz,
    CONSTRAINT chk_offer_amount CHECK (amount > 0),
    CONSTRAINT chk_offer_term CHECK (term_months > 0),
    CONSTRAINT chk_offer_proposed_by CHECK (proposed_by IN ('SYSTEM', 'UNDERWRITER')),
    CONSTRAINT chk_offer_status CHECK (status IN
        ('CALCULATED', 'OFFERED', 'CHOSEN', 'DECLINED', 'EXPIRED')),
    CONSTRAINT chk_offer_comment CHECK (proposed_by <> 'UNDERWRITER' OR comment IS NOT NULL)
);

CREATE UNIQUE INDEX uq_offer_chosen_per_application
    ON offer (application_id) WHERE status = 'CHOSEN';

CREATE TABLE decision (
    id               uuid PRIMARY KEY,
    application_id   uuid NOT NULL UNIQUE REFERENCES application (id),
    type             varchar(11) NOT NULL,
    offer_id         uuid REFERENCES offer (id),
    decided_by       varchar(11) NOT NULL,
    rules_version    text,
    override         boolean     NOT NULL DEFAULT false,
    comment          text,
    reason_spot_id   uuid REFERENCES spot (id),
    decided_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_decision_type CHECK (type IN ('APPROVED', 'ALTERNATIVE', 'REJECTED')),
    CONSTRAINT chk_decision_decided_by CHECK (decided_by IN ('SYSTEM', 'UNDERWRITER')),
    CONSTRAINT chk_decision_override_comment CHECK (override = false OR comment IS NOT NULL),
    CONSTRAINT chk_decision_rejected_reason CHECK (type <> 'REJECTED' OR reason_spot_id IS NOT NULL)
);

CREATE TABLE data_source (
    code             varchar(12) PRIMARY KEY,
    name             text        NOT NULL,
    requires_consent boolean     NOT NULL
);

CREATE TABLE application_source (
    id              uuid PRIMARY KEY,
    application_id  uuid        NOT NULL REFERENCES application (id),
    source_code     varchar(12) NOT NULL REFERENCES data_source (code),
    consent_given   boolean     NOT NULL DEFAULT false,
    connected_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_application_source UNIQUE (application_id, source_code)
);

CREATE TABLE audit_log (
    id             uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES application (id),
    user_id        uuid REFERENCES app_user (id),
    event          varchar(15) NOT NULL,
    from_state     varchar(15),
    to_state       varchar(15),
    payload        jsonb,
    reason         text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_event CHECK (event IN
        ('STATUS_CHANGE', 'SPOT_CREATED', 'SPOT_CLOSED', 'SPOT_REVIEWED',
         'OFFER_CREATED', 'OFFER_CHOSEN', 'DECISION', 'OVERRIDE')),
    CONSTRAINT chk_audit_states CHECK (
        (from_state IS NULL OR from_state IN
            ('DRAFT', 'COLLECTING', 'SUBMITTED', 'AUTO_CHECK', 'UNDERWRITING',
             'INFO_REQUESTED', 'APPROVED', 'COUNTER_OFFER', 'REJECTED',
             'SIGNING', 'ISSUED', 'CANCELLED')) AND
        (to_state IS NULL OR to_state IN
            ('DRAFT', 'COLLECTING', 'SUBMITTED', 'AUTO_CHECK', 'UNDERWRITING',
             'INFO_REQUESTED', 'APPROVED', 'COUNTER_OFFER', 'REJECTED',
             'SIGNING', 'ISSUED', 'CANCELLED')))
);

CREATE INDEX idx_application_company ON application (company_id);
CREATE INDEX idx_application_status ON application (status);
CREATE INDEX idx_spot_application ON spot (application_id);
CREATE INDEX idx_spot_answer_spot ON spot_answer (spot_id);
CREATE INDEX idx_offer_application ON offer (application_id);
CREATE INDEX idx_audit_log_application ON audit_log (application_id, created_at);
CREATE INDEX idx_application_source_application ON application_source (application_id);

INSERT INTO data_source (code, name, requires_consent) VALUES
    ('BKI',         'Бюро кредитных историй',        true),
    ('EGRUL',       'ЕГРЮЛ / ЕГРИП',                 false),
    ('FNS',         'ФНС (долги по налогам)',        false),
    ('OFD',         'ОФД (кассовая выручка)',        true),
    ('MARKETPLACE', 'Маркетплейс (Wildberries)',     true),
    ('ACQUIRING',   'Эквайринг (обороты по карте)',  true),
    ('REPUTATION',  'Репутационная проверка',        false);

INSERT INTO product (id, code, name, min_amount, max_amount, allowed_terms) VALUES
    ('00000000-0000-0000-0000-000000000301', 'WORKING_CAPITAL',
     'Кредит на пополнение оборотных средств', 10000000, 1000000000,
     '[6, 12, 24, 36]'),
    ('00000000-0000-0000-0000-000000000302', 'EQUIPMENT',
     'Кредит на оборудование', 30000000, 1000000000,
     '[12, 24, 36]'),
    ('00000000-0000-0000-0000-000000000303', 'OVERDRAFT',
     'Овердрафт для бизнеса', 5000000, 300000000,
     '[3, 6, 12]');
