CREATE TABLE IF NOT EXISTS underwriting_summary (
    application_id uuid PRIMARY KEY REFERENCES application (id),
    status         varchar(10) NOT NULL,
    generated_by   varchar(8)  NOT NULL,
    items          jsonb       NOT NULL,
    llm            jsonb,
    generated_at   timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    revision       bigint      NOT NULL,
    CONSTRAINT chk_summary_status CHECK (status IN ('ready', 'generating')),
    CONSTRAINT chk_summary_generated_by CHECK (generated_by IN ('TEMPLATE', 'LLM')),
    CONSTRAINT chk_summary_revision CHECK (revision > 0)
);
