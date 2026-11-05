-- Properties and rooms (docs/DATABASE.md §3.2) and file clean-up jobs (§3.6, ADR-018).

CREATE TABLE properties (
    id          uuid PRIMARY KEY,
    owner_id    uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        text          NOT NULL,
    type        text          NOT NULL CHECK (type IN ('APARTMENT', 'HOUSE', 'COMMERCIAL', 'OTHER')),
    address     text          NOT NULL,
    city        text          NULL,
    area_sqm    numeric(8, 2) NULL CHECK (area_sqm >= 0),
    notes       text          NULL,
    archived_at timestamptz   NULL,
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL,
    version     bigint        NOT NULL DEFAULT 0
);

CREATE INDEX properties_owner_archived_idx ON properties (owner_id, archived_at);

CREATE TABLE rooms (
    id          uuid PRIMARY KEY,
    property_id uuid        NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    name        text        NOT NULL,
    room_type   text        NOT NULL
        CHECK (room_type IN ('LIVING', 'KITCHEN', 'BATHROOM', 'TOILET', 'HALLWAY', 'BALCONY', 'OTHER')),
    sort_order  int         NOT NULL CHECK (sort_order >= 0),
    archived_at timestamptz NULL,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 0
);

CREATE INDEX rooms_property_sort_idx ON rooms (property_id, sort_order);

CREATE TABLE outbox_events (
    id              uuid PRIMARY KEY,
    type            text        NOT NULL CHECK (type IN ('DELETE_PROPERTY_FILES', 'DELETE_USER_FILES')),
    payload         jsonb       NOT NULL,
    status          text        NOT NULL CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    attempts        int         NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    last_error      text        NULL,
    created_at      timestamptz NOT NULL
);

CREATE INDEX outbox_events_pending_idx ON outbox_events (next_attempt_at) WHERE status = 'PENDING';
