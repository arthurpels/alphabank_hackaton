ALTER TABLE spot_answer ADD COLUMN contract_amount bigint;
ALTER TABLE spot_answer ADD COLUMN contract_term_months integer;
ALTER TABLE spot_answer ADD CONSTRAINT chk_spot_answer_contract CHECK (
    (contract_amount IS NULL AND contract_term_months IS NULL)
    OR (contract_amount > 0 AND contract_term_months > 0));
ALTER TABLE decision ADD COLUMN decided_by_user_id uuid REFERENCES app_user (id);
