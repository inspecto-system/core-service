-- Defects, their observations in inspections and status history (docs/DATABASE.md §3.5, ADR-003,
-- T-203).
--
-- Keys that can be reached by two cascade paths when a property or an account is deleted are
-- checked at commit (DEFERRABLE INITIALLY DEFERRED), see V5. core-service rejects deleting a single
-- room, category or draft inspection that is still referenced before it reaches the database.

CREATE TABLE defects (
    id                  uuid PRIMARY KEY,
    property_id         uuid        NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    room_id             uuid        NOT NULL REFERENCES rooms (id) DEFERRABLE INITIALLY DEFERRED,
    category_id         uuid        NOT NULL REFERENCES defect_categories (id) DEFERRABLE INITIALLY DEFERRED,
    element_group       text        NOT NULL
        CHECK (element_group IN ('WALLS', 'CEILING', 'FLOOR', 'WINDOWS', 'DOORS', 'PLUMBING', 'ELECTRICAL', 'OTHER')),
    title               text        NOT NULL,
    description         text        NULL,
    severity            text        NOT NULL CHECK (severity IN ('COSMETIC', 'MINOR', 'MAJOR', 'CRITICAL')),
    current_status      text        NOT NULL CHECK (current_status IN ('OPEN', 'RESOLVED')),
    -- Deleting a draft inspection deletes the defects first found in it.
    first_inspection_id uuid        NOT NULL REFERENCES inspections (id) ON DELETE CASCADE,
    created_by          uuid        NOT NULL REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    version             bigint      NOT NULL DEFAULT 0
);

CREATE INDEX defects_property_status_idx ON defects (property_id, current_status);
CREATE INDEX defects_room_idx ON defects (room_id);
CREATE INDEX defects_category_idx ON defects (category_id);
CREATE INDEX defects_first_inspection_idx ON defects (first_inspection_id);

CREATE TABLE defect_observations (
    id                uuid PRIMARY KEY,
    defect_id         uuid        NOT NULL REFERENCES defects (id) ON DELETE CASCADE,
    inspection_id     uuid        NOT NULL REFERENCES inspections (id) ON DELETE CASCADE,
    checklist_item_id uuid        NULL
        REFERENCES inspection_checklist_items (id) DEFERRABLE INITIALLY DEFERRED,
    status            text        NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    severity          text        NOT NULL CHECK (severity IN ('COSMETIC', 'MINOR', 'MAJOR', 'CRITICAL')),
    comment           text        NULL,
    observed_by       uuid        NOT NULL REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    version           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT defect_observations_defect_inspection_uq UNIQUE (defect_id, inspection_id)
);

CREATE INDEX defect_observations_inspection_idx ON defect_observations (inspection_id);

-- Append-only: rows are only inserted, and removed together with their defect.
CREATE TABLE defect_status_history (
    id            uuid PRIMARY KEY,
    defect_id     uuid        NOT NULL REFERENCES defects (id) ON DELETE CASCADE,
    from_status   text        NULL CHECK (from_status IN ('OPEN', 'RESOLVED')),
    to_status     text        NOT NULL CHECK (to_status IN ('OPEN', 'RESOLVED')),
    inspection_id uuid        NULL REFERENCES inspections (id) DEFERRABLE INITIALLY DEFERRED,
    changed_by    uuid        NOT NULL REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED,
    changed_at    timestamptz NOT NULL,
    comment       text        NULL
);

CREATE INDEX defect_status_history_defect_idx ON defect_status_history (defect_id, changed_at);
