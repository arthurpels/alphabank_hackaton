ALTER TABLE application ADD COLUMN as_of date;
ALTER TABLE fork_snapshot ADD COLUMN as_of date;

UPDATE application a
SET as_of = first_snapshot.first_as_of
FROM (
    SELECT application_id, (MIN(calculated_at))::date AS first_as_of
    FROM fork_snapshot
    GROUP BY application_id
) first_snapshot
WHERE a.id = first_snapshot.application_id;

UPDATE fork_snapshot f
SET as_of = a.as_of
FROM application a
WHERE a.id = f.application_id;

ALTER TABLE fork_snapshot ALTER COLUMN as_of SET NOT NULL;
