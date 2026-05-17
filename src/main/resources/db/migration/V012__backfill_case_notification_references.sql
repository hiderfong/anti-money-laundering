-- Backfill historical case notifications with explicit case relations.
-- Some old seed/mock notifications only stored the business case number in content,
-- which made the UI unable to open the corresponding case by stable case ID.

UPDATE t_notification n
JOIN t_case c
  ON c.case_no = REGEXP_SUBSTR(n.content, '(E2E)?CASE[0-9]+')
SET n.related_type = 'CASE',
    n.related_id = CAST(c.id AS CHAR)
WHERE n.type = 'CASE'
  AND (n.related_type IS NULL OR n.related_type = '' OR n.related_id IS NULL OR n.related_id = '');
