CREATE TABLE users (
    id                    uuid PRIMARY KEY,
    email                 text        NOT NULL,
    password_hash         text        NOT NULL,
    full_name             text        NOT NULL,
    role                  text        NOT NULL CHECK (role IN ('ADMIN', 'INSPECTOR', 'CLIENT')),
    locale                text        NOT NULL DEFAULT 'ru' CHECK (locale IN ('ru', 'en')),
    failed_login_attempts int         NOT NULL DEFAULT 0,
    locked_until          timestamptz NULL,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    version               bigint      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id  uuid        NOT NULL,
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    user_agent text        NULL
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
