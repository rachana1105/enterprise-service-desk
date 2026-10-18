-- Rows written by the smoke test, used as database evidence (run by verify-all scripts).
SELECT id, title, category, priority, status, requester_id, assignee_id, created_at FROM incidents ORDER BY id;
SELECT incident_id, action, detail, actor_id, created_at FROM incident_events ORDER BY id;
SELECT recipient_id, message, read_flag, created_at FROM notifications ORDER BY id;
SELECT id, name, email, role FROM users ORDER BY id;
