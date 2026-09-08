-- Run only against disposable local/test data with psql.
-- Rolls back all fixtures. Existing production data is not representative of this synthetic workload.
BEGIN;
INSERT INTO recipes (id, version, title, servings, vegetarian)
SELECT -n, 0, 'Plan fixture ' || n, 1 + n % 8, n % 2 = 0
FROM generate_series(1, 20000) n;
INSERT INTO recipe_ingredients (recipe_id, position, text)
SELECT -n, 0, CASE WHEN n % 100 = 0 THEN 'saffron rice' ELSE 'rice and beans' END
FROM generate_series(1, 20000) n;
INSERT INTO recipe_instructions (recipe_id, position, text)
SELECT -n, 0, CASE WHEN n % 100 = 0 THEN 'Preheat the oven' ELSE 'Mix well' END
FROM generate_series(1, 20000) n;
ANALYZE recipes;
ANALYZE recipe_ingredients;
ANALYZE recipe_instructions;

-- Selective substring; check the lower(text) trigram index and actual rows/buffers.
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.title, r.description, r.servings, r.vegetarian
FROM recipes r
WHERE EXISTS (
    SELECT 1 FROM recipe_ingredients i
    WHERE i.recipe_id = r.id AND lower(i.text) LIKE '%saffron%' ESCAPE '!'
)
ORDER BY r.id LIMIT 21;

-- Broad exclusion can require scanning: deadlines and offset bounds still matter.
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.title, r.description, r.servings, r.vegetarian
FROM recipes r
WHERE NOT EXISTS (
    SELECT 1 FROM recipe_ingredients i
    WHERE i.recipe_id = r.id AND lower(i.text) LIKE '%saffron%' ESCAPE '!'
)
ORDER BY r.id LIMIT 21 OFFSET 10000;

EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.title, r.description, r.servings, r.vegetarian
FROM recipes r
WHERE EXISTS (
    SELECT 1 FROM recipe_instructions s
    WHERE s.recipe_id = r.id AND lower(s.text) LIKE '%oven%' ESCAPE '!'
)
ORDER BY r.id LIMIT 21;

-- Evaluate the optional servings index with the same data, without retaining it.
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM recipes WHERE servings = 4 ORDER BY id LIMIT 21;
CREATE INDEX plan_fixture_servings_id ON recipes (servings, id);
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM recipes WHERE servings = 4 ORDER BY id LIMIT 21;
ROLLBACK;
