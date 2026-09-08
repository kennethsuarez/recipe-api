CREATE INDEX recipe_title_trgm ON recipes USING gin (lower(title) gin_trgm_ops);
