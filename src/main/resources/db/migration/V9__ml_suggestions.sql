-- Category suggestions of ml-service (T-602, DATABASE.md ml_suggestions): what the model suggested
-- for a photo and, once the user creates the defect, what they chose — the usefulness metric of
-- ML_DOCUMENTATION.md §5. A suggestion belongs to its user and disappears with the account; the
-- defect link is cleared when the defect is deleted.
CREATE TABLE ml_suggestions (
    id                 uuid PRIMARY KEY,
    photo_id           uuid        NOT NULL,
    requested_by       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    model_version      text        NOT NULL,
    predictions        jsonb       NOT NULL,
    defect_id          uuid        NULL REFERENCES defects (id) ON DELETE SET NULL,
    chosen_category_id uuid        NULL,
    decision           text        NULL
        CHECK (decision IN ('ACCEPTED_TOP1', 'ACCEPTED_OTHER', 'REJECTED')),
    created_at         timestamptz NOT NULL,
    decided_at         timestamptz NULL,
    CONSTRAINT ml_suggestions_decided_ck CHECK ((decision IS NULL) = (decided_at IS NULL))
);

CREATE INDEX ml_suggestions_requested_by_idx ON ml_suggestions (requested_by);
CREATE INDEX ml_suggestions_defect_idx ON ml_suggestions (defect_id);
