CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SEQUENCE recipe_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE recipes (
    id bigint PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    title varchar(200) NOT NULL CHECK (length(trim(title)) > 0),
    description varchar(10000),
    servings integer NOT NULL CHECK (servings > 0),
    vegetarian boolean NOT NULL DEFAULT false
);
CREATE TABLE recipe_ingredients (
    recipe_id bigint NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    position integer NOT NULL CHECK (position >= 0),
    text varchar(1000) NOT NULL CHECK (length(trim(text)) > 0),
    PRIMARY KEY (recipe_id, position)
);
CREATE TABLE recipe_instructions (
    recipe_id bigint NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    position integer NOT NULL CHECK (position >= 0),
    text varchar(2000) NOT NULL CHECK (length(trim(text)) > 0),
    PRIMARY KEY (recipe_id, position)
);
CREATE INDEX ingredient_text_trgm ON recipe_ingredients USING gin (lower(text) gin_trgm_ops);
CREATE INDEX instruction_text_trgm ON recipe_instructions USING gin (lower(text) gin_trgm_ops);
