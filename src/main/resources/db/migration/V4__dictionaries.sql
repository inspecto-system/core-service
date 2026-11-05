-- Defect categories and checklist templates (docs/DATABASE.md §3.3, ADR-017).

CREATE TABLE defect_categories (
    id            uuid PRIMARY KEY,
    code          text        NULL,
    owner_id      uuid        NULL REFERENCES users (id) ON DELETE CASCADE,
    names         jsonb       NOT NULL,
    element_group text        NULL
        CHECK (element_group IN ('WALLS', 'CEILING', 'FLOOR', 'WINDOWS', 'DOORS', 'PLUMBING', 'ELECTRICAL', 'OTHER')),
    is_active     boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CHECK (owner_id IS NOT NULL OR code IS NOT NULL)
);

CREATE UNIQUE INDEX defect_categories_system_code_uq ON defect_categories (code) WHERE owner_id IS NULL;
CREATE INDEX defect_categories_owner_idx ON defect_categories (owner_id);

CREATE TABLE checklist_templates (
    id         uuid PRIMARY KEY,
    code       text        NOT NULL UNIQUE,
    names      jsonb       NOT NULL,
    is_system  boolean     NOT NULL,
    owner_id   uuid        NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    CHECK (is_system = (owner_id IS NULL))
);

CREATE TABLE checklist_template_items (
    id            uuid PRIMARY KEY,
    template_id   uuid  NOT NULL REFERENCES checklist_templates (id) ON DELETE CASCADE,
    element_group text  NOT NULL
        CHECK (element_group IN ('WALLS', 'CEILING', 'FLOOR', 'WINDOWS', 'DOORS', 'PLUMBING', 'ELECTRICAL', 'OTHER')),
    titles        jsonb NOT NULL,
    sort_order    int   NOT NULL CHECK (sort_order >= 0)
);

CREATE INDEX checklist_template_items_template_idx ON checklist_template_items (template_id, sort_order);

-- System categories. CRACK, PEELING, STAIN, MOLD and SPALLING correspond to the classes of the
-- ML model (docs/ML_DOCUMENTATION.md §3.5); the others cover typical finishing defects found
-- when accepting an apartment.
INSERT INTO defect_categories (id, code, owner_id, names, element_group, created_at, updated_at)
SELECT gen_random_uuid(), c.code, NULL, c.names::jsonb, c.element_group, now(), now()
FROM (VALUES
    ('CRACK',            '{"ru": "Трещина", "en": "Crack"}', NULL),
    ('PEELING',          '{"ru": "Отслоение покрытия", "en": "Peeling"}', NULL),
    ('STAIN',            '{"ru": "Пятно, следы протечки", "en": "Stain or leak mark"}', NULL),
    ('MOLD',             '{"ru": "Плесень", "en": "Mold"}', NULL),
    ('SPALLING',         '{"ru": "Скол, разрушение поверхности", "en": "Spalling"}', NULL),
    ('UNEVEN_SURFACE',   '{"ru": "Неровность поверхности", "en": "Uneven surface"}', NULL),
    ('WALLPAPER',        '{"ru": "Дефект обоев", "en": "Wallpaper defect"}', 'WALLS'),
    ('TILE',             '{"ru": "Дефект плитки", "en": "Tile defect"}', NULL),
    ('FLOOR_COVERING',   '{"ru": "Дефект напольного покрытия", "en": "Floor covering defect"}', 'FLOOR'),
    ('WINDOW',           '{"ru": "Дефект окна", "en": "Window defect"}', 'WINDOWS'),
    ('DOOR',             '{"ru": "Дефект двери", "en": "Door defect"}', 'DOORS'),
    ('PLUMBING_LEAK',    '{"ru": "Протечка сантехники", "en": "Plumbing leak"}', 'PLUMBING'),
    ('ELECTRICAL_FAULT', '{"ru": "Неисправность электрики", "en": "Electrical fault"}', 'ELECTRICAL'),
    ('OTHER',            '{"ru": "Прочее", "en": "Other"}', NULL)
) AS c (code, names, element_group);

-- System checklist applied to every selected room of an inspection; items that do not apply to a
-- room are marked NOT_APPLICABLE by the inspector.
INSERT INTO checklist_templates (id, code, names, is_system, owner_id, created_at)
VALUES (gen_random_uuid(), 'ROOM_STANDARD',
        '{"ru": "Стандартная приёмка помещения", "en": "Standard room acceptance"}', true, NULL, now());

INSERT INTO checklist_template_items (id, template_id, element_group, titles, sort_order)
SELECT gen_random_uuid(), t.id, i.element_group, i.titles::jsonb, i.sort_order
FROM checklist_templates t
CROSS JOIN (VALUES
    ('WALLS',      '{"ru": "Стены: ровность, трещины, отделка", "en": "Walls: evenness, cracks, finish"}', 0),
    ('CEILING',    '{"ru": "Потолок: ровность, трещины, следы протечек", "en": "Ceiling: evenness, cracks, leak marks"}', 1),
    ('FLOOR',      '{"ru": "Пол: ровность, покрытие, плинтусы", "en": "Floor: evenness, covering, skirting"}', 2),
    ('WINDOWS',    '{"ru": "Окна: открывание, уплотнители, стёкла, откосы", "en": "Windows: opening, seals, glass, reveals"}', 3),
    ('DOORS',      '{"ru": "Двери: открывание, фурнитура, зазоры", "en": "Doors: opening, hardware, gaps"}', 4),
    ('PLUMBING',   '{"ru": "Сантехника: подключение, отсутствие протечек", "en": "Plumbing: connections, no leaks"}', 5),
    ('ELECTRICAL', '{"ru": "Электрика: розетки, выключатели, освещение", "en": "Electrical: sockets, switches, lighting"}', 6),
    ('OTHER',      '{"ru": "Прочее: радиаторы, вентиляция", "en": "Other: radiators, ventilation"}', 7)
) AS i (element_group, titles, sort_order)
WHERE t.code = 'ROOM_STANDARD';
