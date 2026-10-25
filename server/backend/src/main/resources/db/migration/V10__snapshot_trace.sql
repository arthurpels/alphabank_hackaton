ALTER TABLE fork_snapshot ADD COLUMN score_request text;
ALTER TABLE fork_snapshot ADD COLUMN request_hash varchar(64);
ALTER TABLE fork_snapshot ADD COLUMN score_response text;
ALTER TABLE fork_snapshot ADD COLUMN engine_version varchar(32);
ALTER TABLE fork_snapshot ADD COLUMN verdicts jsonb;
ALTER TABLE decision ADD COLUMN fork_snapshot_id uuid REFERENCES fork_snapshot (id);
ALTER TABLE decision ADD COLUMN request_hash varchar(64);
