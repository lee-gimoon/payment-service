-- V1 recorded a finish time when an approval needed review, but review is not a final state.
UPDATE payment_attempts
SET finished_at = NULL
WHERE status IN ('APPROVING', 'UNKNOWN', 'REVIEW_REQUIRED');

-- Only final attempts carry a finish time.
ALTER TABLE payment_attempts ADD CONSTRAINT payment_attempts_finished_at_check CHECK (
    (status IN ('AUTH_CANCELED', 'AUTH_FAILED', 'SUCCEEDED', 'FAILED')) = (finished_at IS NOT NULL)
);
