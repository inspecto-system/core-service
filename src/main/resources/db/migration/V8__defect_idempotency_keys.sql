-- Idempotency-Key of POST /inspections/{id}/defects (platform/api core 0.5.0, T-308): a repeated
-- request of the Android offline queue returns the defect created by the first one. A key belongs
-- to one user; it disappears together with its defect.
CREATE TABLE defect_idempotency_keys (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    key        text        NOT NULL CHECK (length(key) BETWEEN 8 AND 64),
    defect_id  uuid        NOT NULL REFERENCES defects (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    CONSTRAINT defect_idempotency_keys_user_key_uq UNIQUE (user_id, key)
);

CREATE INDEX defect_idempotency_keys_defect_idx ON defect_idempotency_keys (defect_id);
