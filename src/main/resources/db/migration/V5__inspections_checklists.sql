-- Inspections and their checklists (docs/DATABASE.md §3.4, T-201, T-202).
--
-- inspector_id is checked at commit for the same reason as room_id below: deleting an account
-- cascades to its properties and their inspections.

CREATE TABLE inspections (
    id           uuid PRIMARY KEY,
    property_id  uuid        NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    inspector_id uuid        NOT NULL REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED,
    title        text        NULL,
    inspected_at timestamptz NOT NULL,
    status       text        NOT NULL CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED')),
    notes        text        NULL,
    completed_at timestamptz NULL,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    version      bigint      NOT NULL DEFAULT 0,
    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

CREATE INDEX inspections_property_inspected_idx ON inspections (property_id, inspected_at DESC);

-- The room_id key is checked at commit (DEFERRABLE INITIALLY DEFERRED): deleting a property
-- cascades to rooms and to inspections in an unspecified order, and an immediate check could see
-- checklist items that are about to be deleted. Deleting a single room used by an inspection still
-- fails; core-service reports that case as RESOURCE_IN_USE before it reaches the database.
CREATE TABLE inspection_checklist_items (
    id               uuid PRIMARY KEY,
    inspection_id    uuid        NOT NULL REFERENCES inspections (id) ON DELETE CASCADE,
    room_id          uuid        NOT NULL REFERENCES rooms (id) DEFERRABLE INITIALLY DEFERRED,
    template_item_id uuid        NULL REFERENCES checklist_template_items (id),
    element_group    text        NOT NULL
        CHECK (element_group IN ('WALLS', 'CEILING', 'FLOOR', 'WINDOWS', 'DOORS', 'PLUMBING', 'ELECTRICAL', 'OTHER')),
    title            text        NOT NULL,
    result           text        NOT NULL DEFAULT 'NOT_CHECKED'
        CHECK (result IN ('NOT_CHECKED', 'OK', 'DEFECT', 'NOT_APPLICABLE')),
    comment          text        NULL,
    sort_order       int         NOT NULL CHECK (sort_order >= 0),
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL DEFAULT 0
);

CREATE INDEX inspection_checklist_items_inspection_idx
    ON inspection_checklist_items (inspection_id, room_id, sort_order);
CREATE INDEX inspection_checklist_items_room_idx ON inspection_checklist_items (room_id);
