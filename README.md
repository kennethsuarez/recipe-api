# Recipe API

Java 17 / Spring Boot 3.5.0 / Maven / PostgreSQL 14.17. Supports recipe CRUD and composable search, with no authentication or ownership.

## Run with Docker Compose

Install Docker with Compose and start the Docker engine in Linux-container mode. From the project directory, run:

```sh
docker compose up --build
```

This builds and starts both the API and PostgreSQL 14.17. No local Java or Maven installation is needed. The multi-stage Dockerfile uses the Maven wrapper and Java 17 to build the jar and run the database-independent tests, then runs the application as a non-root user in a Java 17 JRE image. The first build requires internet access to download images and Maven dependencies.

Compose waits for PostgreSQL's health check before starting the API. Flyway applies migrations during application startup. Once startup completes, access the API at `http://localhost:8080/api/recipes` and check readiness at `http://localhost:8080/actuator/health`.

Use `docker compose up --build -d` to run in the background, `docker compose logs -f api` to view application logs, and `docker compose down` to stop and remove the containers. Database data remains in the named volume. Both published ports are bound to localhost, and the database uses development credentials `recipes/recipes`.

## Run locally with Maven

For IDE development, install JDK 17 and start only the database with Compose (or supply an existing PostgreSQL 14.17 database). If the full Compose stack is already running, first run `docker compose stop api` to free port 8080.

```powershell
docker compose up -d postgres
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

On macOS/Linux use `./mvnw`. The local API listens on port 8080 and connects to PostgreSQL on localhost. The containerized API instead connects through the Compose service name `postgres`.

For an existing database, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Flyway creates the schema and `pg_trgm` extension, then Hibernate validates the mappings. The migration user must be allowed to create that extension, or an administrator must provision it first. Use deployment-specific credentials outside local development.

Build an executable jar with `./mvnw verify`; run `java -jar target/recipe-api-0.0.1-SNAPSHOT.jar`.

## Contract

`docs/technical-specs.md` is authoritative. The implementation follows its `/api/recipes` route convention instead of the shorter routes in the supplemental design decisions. `docs/design-decisions.md` supplies business semantics. Partial updates are out of scope; PATCH is not implemented.

| Method | Route | Result |
| --- | --- | --- |
| POST | /api/recipes | 201, recipe DTO and Location header |
| GET | /api/recipes/{id} | 200, recipe DTO |
| GET | /api/recipes | 200, filtered slice |
| PUT | /api/recipes/{id} | 200, full replacement DTO |
| DELETE | /api/recipes/{id} | 204, no body |

Missing recipes return 404, including PUT and DELETE. Invalid input returns 400, oversized bodies 413, overlapping write conflicts 409, and detected temporary saturation/database unavailability 503 with Retry-After. Errors use Problem Details JSON; validation errors include an `errors` array. Do not automatically retry writes.

Example request (save as `recipe.json`):

```json
{
  "title": "Rice bowl",
  "description": "A simple meal.\n\nServe warm.",
  "servings": 4,
  "vegetarian": true,
  "ingredients": ["200g rice", "1 pinch salt"],
  "instructions": ["Boil water", "Cook the rice"]
}
```

```sh
curl -i -H "Content-Type: application/json" --data-binary @recipe.json http://localhost:8080/api/recipes
curl "http://localhost:8080/api/recipes?vegetarian=true&servings=4&includeIngredient=rice&excludeIngredient=chicken&instruction=cook&page=0&size=20"
```

In Windows PowerShell use `curl.exe`. PUT uses the same body schema as POST.

A recipe response contains `id`, `title`, `description`, `servings`, `vegetarian`, `ingredients`, and `instructions`. A collection response is:

```json
{"results":[],"page":0,"size":20,"hasNext":false}
```

IDs are sequence-generated 64-bit integers; gaps are expected. Duplicate titles are allowed. Vegetarian is author-supplied and independent of ingredients. On POST and PUT, omitted or null `vegetarian` defaults to false, omitted or null `instructions` defaults to [], and omitted or null `description` becomes null. Title, servings, and ingredients cannot be omitted or null. Null/blank list entries are invalid. PUT replaces every field and both lists atomically.

Titles, ingredients, and steps have line breaks replaced with spaces and surrounding Unicode whitespace stripped; other internal spacing is preserved. Description text is preserved exactly, including paragraphs. Validation applies to submitted size limits and normalized content. Unknown JSON fields and fractional servings are rejected.

### Search

All categories combine with AND. Parameters:

- `vegetarian=true` restricts results; false or omission does not.
- `servings` is an exact positive integer match.
- Repeat `includeIngredient` for terms that must each match an ingredient.
- Repeat `excludeIngredient` for terms that must match no ingredient.
- `instruction` matches within any single step.
- `page` defaults to 0; `size` defaults to 20.

Ingredient and instruction search uses case-insensitive literal substrings, with SQL pattern metacharacters escaped. Terms are trimmed, lowercased and deduplicated; blank terms are ignored. Use repeated query parameters for multiple terms; commas within a term are literal text. Including and excluding the same term yields an empty successful slice. There is no stemming or phrase matching across steps.

Slices have no exact total. Ordering is ID ascending. Separate page requests do not share a snapshot; future deep browsing can use keyset pagination without changing filter semantics.

### Finite limits

| Input | Limit |
| --- | --- |
| Title | 200 characters |
| Description | 10,000 characters |
| Ingredient entry | 1,000 characters |
| Instruction step | 2,000 characters |
| Ingredients | 1–100 entries |
| Instructions | 0–100 entries |
| Search term / instruction query | 200 characters |
| Include / exclude terms | 10 each, before deduplication |
| Page size | 1–100 |
| Page offset (page × size) | 10,000 by default |
| Raw body | 524,288 bytes (512 KiB), including chunked requests |
| HTTP request headers / request line | 16 KiB |

Text limits use Java UTF-16 length. Excess input is rejected. The raw body bound can reject a large JSON representation even when individual fields fit, for example extensively escaped text.

## Design and operations

Controllers perform HTTP handling, services own normalization/business logic and transactions, and repositories own persistence queries. Dedicated records and a mapper prevent entity serialization. Lombok supplies constructors and entity accessors, without association-based equality or string output.

Reads use explicit root projections with SQL pagination and one extra root for hasNext, then two ordered bulk child projections. A nonempty slice/detail uses at most three SELECTs; an empty slice uses one. Only returned IDs receive child loading, and matching a child does not truncate the returned lists. Search uses correlated EXISTS/NOT EXISTS without multiplying root rows.

Read assembly uses a short read-only repeatable-read transaction. OSIV is disabled and collection-fetch pagination fails fast. Ordered lazy element collections have (recipe_id, position) primary keys. Writes use JPA aggregate versioning; PUT explicitly forces a version increment even for child-only or identical replacements. DELETE checks the loaded version. This protects overlapping server transactions, not stale client forms submitted later; ETag/If-Match is deferred.

Flyway manages schema and GIN indexes on lower(text), aligned with the query expression. JDBC writes batch at 50. No cache is used. A servings index and vegetarian indexes are deferred until representative plans justify them.

Defaults are explicit and can be overridden per deployment:

| Environment variable | Default |
| --- | --- |
| DB_POOL_SIZE / DB_MIN_IDLE | 10 / 2 |
| DB_ACQUIRE_TIMEOUT_MS | 2000 |
| DB_STATEMENT_TIMEOUT_MS / DB_LOCK_TIMEOUT_MS | 3000 / 1000 |
| HTTP_MAX_THREADS / HTTP_MIN_THREADS | 48 / 4 |
| HTTP_MAX_CONNECTIONS / HTTP_ACCEPT_COUNT | 128 / 32 |
| RECIPE_HTTP_TASK_QUEUE_SIZE | 32 |
| RECIPE_MAX_CONCURRENT_REQUESTS | 32 |
| RECIPE_MAX_OFFSET | 10000 |
| RECIPE_MAX_REQUEST_BYTES | 524288 |

The application admission semaphore queues no requests and returns 503 when full. Tomcat also has a bounded task queue, connection backlog, 5-second connection/upload timeout and 10-second keepalive timeout. JDBC connection and socket timeouts are 3 and 5 seconds. At connector saturation a connection may be rejected before HTTP handling; deployment admission control must provide controlled rejection ahead of this boundary. If a rate limiter is deployed, it should return 429. Budget total pool connections across replicas and reserve database capacity for operations/migrations. These defaults are starting bounds, not measured capacity claims.

Actuator records route-based HTTP timing/error metrics, Hikari pool gauges, and JVM/GC metrics; only health is exposed by default at `/actuator/health`. Expose metrics solely on a protected management interface when deploying and configure collection/alerting there. Console logging is structured JSON, slow SQL over 500 ms is logged without parameter logging, and normal operation does not enable Hibernate statistics. Avoid payloads, recipe IDs as metric labels, and SQL bind-value logging.

## Verification

`./mvnw verify` runs controller, service, normalization and admission tests without a database. The PostgreSQL suite is explicit and requires a working Docker engine:

```sh
./mvnw verify -Ppostgres-it
```

It starts PostgreSQL 14.17, applies real migrations, and tests query counts for 1/20/100 results through HTTP serialization, complete ordered children, literal filters/exclusion, pagination, version conflicts, atomic rollback and statement deadlines. It does not silently skip when Docker is missing.

Run `psql -v ON_ERROR_STOP=1 -f docs/explain-search.sql` against a disposable migrated database to inspect selective, broad/exclusion, instruction, deep-offset and candidate servings-index plans. The fixture is rolled back. Record actual rows, execution time and buffers; the optimizer need not select an index for broad or short terms.

Implementation-environment verification: the unit/controller/filter suite passed, and the executable jar was built. The PostgreSQL profile compiled but could not start because Docker is unavailable. Database query plans, PostgreSQL runtime assertions, lock-contention timing and deployment saturation/load checks remain to be run in an environment with PostgreSQL/Docker. No throughput or concurrency capacity claim is made.
