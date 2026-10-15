-- Widen shifts.z_report_url to TEXT: the generated Z-Report (close-of-shift report)
-- is ~1 KB, but the previous VARCHAR(500) truncated it on persistence.
ALTER TABLE shifts ALTER COLUMN z_report_url TYPE TEXT;
