ALTER TABLE spot ADD COLUMN answer_options jsonb;
ALTER TABLE spot_answer ADD COLUMN option_code varchar(20);
