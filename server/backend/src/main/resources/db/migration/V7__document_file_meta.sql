ALTER TABLE document ADD COLUMN original_name text NOT NULL DEFAULT '';
ALTER TABLE document ADD COLUMN content_type text NOT NULL DEFAULT 'application/octet-stream';
