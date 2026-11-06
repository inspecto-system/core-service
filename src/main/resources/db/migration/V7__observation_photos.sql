-- Photos attached to observations (docs/DATABASE.md §3.5, T-206) and the outbox job that deletes a
-- detached photo in media-service (ADR-018).

-- photo_id refers to inspecto_media.photos (another database, no foreign key). It is unique: a photo
-- belongs to one observation only.
CREATE TABLE observation_photos (
    id             uuid PRIMARY KEY,
    observation_id uuid        NOT NULL REFERENCES defect_observations (id) ON DELETE CASCADE,
    photo_id       uuid        NOT NULL UNIQUE,
    caption        text        NULL,
    sort_order     int         NOT NULL CHECK (sort_order >= 0),
    created_at     timestamptz NOT NULL
);

CREATE INDEX observation_photos_observation_idx ON observation_photos (observation_id, sort_order);

ALTER TABLE outbox_events DROP CONSTRAINT outbox_events_type_check;
ALTER TABLE outbox_events ADD CONSTRAINT outbox_events_type_check
    CHECK (type IN ('DELETE_PROPERTY_FILES', 'DELETE_USER_FILES', 'DELETE_PHOTO'));
