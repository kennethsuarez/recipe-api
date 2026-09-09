# Recipe API

This application is a sample backend for an app that acts a central for user recipes. It shows a quick glance at the culmination of my years of expertise in backend development.

Java 17 / Spring Boot 3.5.0 / Maven / PostgreSQL 14.17. Supports recipe CRUD and composable search, with no authentication or ownership.

## Setup

The application can be started using either docker or manual local run. The docker approach is much quicker for local run because it already includes the database setup in the container.

### Run with Docker Compose

Install Docker with Compose and start the Docker engine in Linux-container mode. From the project directory, run:

```sh
docker compose up --build
```

This builds and starts both the API and PostgreSQL 14.17. No local Java or Maven installation is needed. The multi-stage Dockerfile uses the Maven wrapper and Java 17 to build the jar and run the database-independent tests, then runs the application as a non-root user in a Java 17 JRE image. The first build requires internet access to download images and Maven dependencies.

Compose waits for PostgreSQL's health check before starting the API. Flyway applies migrations during application startup. Once startup completes, access the API at `http://localhost:8080/api/recipes` and check readiness at `http://localhost:8080/actuator/health`.

Use `docker compose up --build -d` to run in the background, `docker compose logs -f api` to view application logs, and `docker compose down` to stop and remove the containers. Database data remains in the named volume. Both published ports are bound to localhost, and the database uses development credentials `recipes/recipes`.

### Run locally with Maven

For IDE development, install JDK 17 and start only the database with Compose (or supply an existing PostgreSQL 14.17 database). If the full Compose stack is already running, first run `docker compose stop api` to free port 8080.

```powershell
docker compose up -d postgres
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

On macOS/Linux use `./mvnw`. The local API listens on port 8080 and connects to PostgreSQL on localhost. The containerized API instead connects through the Compose service name `postgres`.

For an existing database, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Flyway creates the schema and `pg_trgm` extension, then Hibernate validates the mappings. The migration user must be allowed to create that extension, or an administrator must provision it first. Use deployment-specific credentials outside local development.

Build an executable jar with `./mvnw verify`; run `java -jar target/recipe-api-0.0.1-SNAPSHOT.jar`.

## OpenAPI and Swagger UI

In local and non-production profiles, the interactive Swagger UI is available at
`http://localhost:8080/docs` and the OpenAPI 3 JSON contract at
`http://localhost:8080/docs-json`. The contract documents every recipe operation,
validation bounds, repeatable search filters, response schema, and expected error
status. The API has no authentication or ownership, so the contract intentionally
defines no security scheme.

## Contract

| Method | Route | Result |
| --- | --- | --- |
| POST | /api/recipes | 201, recipe DTO and Location header |
| GET | /api/recipes/{id} | 200, recipe DTO |
| GET | /api/recipes | 200, filtered slice |
| PUT | /api/recipes/{id} | 200, full replacement DTO |
| DELETE | /api/recipes/{id} | 204, no body |

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


## Design decisions and rationale

This section explains the current implementation, the problem each choice addresses, and its tradeoffs. The technology stack and several business rules come from the assignment; they are not all discretionary architecture choices.

### 1. Explicit layers and constructor injection

**Choice:** Requests follow this path:

```text
RecipeController
      |
RecipeService -> RecipeServiceImpl
      |              |
      |        RecipeValidation / RecipeMapper
      |
RecipeRepository + RecipeReadRepositoryImpl
      |
PostgreSQL
```

The controller handles routes, request binding, validation entry points, and HTTP responses. The service coordinates application rules and transactions. Repositories own persistence queries. Dependencies are constructor-injected, with final fields where appropriate.

**Why:** This follows single responsibility (separation of concern) and makes responsibilities easy to locate and test. Changing an HTTP response should not require editing persistence logic, and a controller test should not need PostgreSQL.


### 2. Separate request, response, and persistence models

**Choice:** Use Java records for `RecipeRequest`, `RecipeResponse`, `RecipeSearch`, and `RecipeSlice`; keep the mutable JPA `Recipe` entity separate. Response records copy their lists with `List.copyOf`.

**Why:** Clients receive an explicit API contract rather than Hibernate-managed objects. This avoids exposing persistence details or allowing serialization to traverse lazy associations. Defensive list copies keep responses independent of subsequent changes to managed collections.

POST and PUT share `RecipeRequest` because both accept the same complete set of fields and validation rules. Separate create/update DTOs would currently duplicate that contract.

**Tradeoff:** Mapping is required. Records make fields final but do not automatically make referenced collections immutable, which is why response lists are copied. If creation and replacement rules diverge, separate request types would become useful.

### 3. A dedicated mapper rather than builders in service methods

**Choice:** `RecipeMapper` contains `toEntity`, `replace`, and two `toResponse` methods.

**Why:** Creation, replacement, detail reads, and search all need the same field mapping. Centralizing it avoids repeated assignments and keeps services focused on the operation. One response method maps an entity used by writes; the other maps the projections used by reads.

A builder is a way to construct an object; it does not remove the need to decide how fields map. Building responses inside both service methods would duplicate that decision. The response record constructor is sufficient for this small model.

**Tradeoff:** Manual mapping must be updated when fields change. MapStruct or a builder inside the mapper could help if the model becomes substantially larger, but neither is necessary here. Replacement mutates the existing managed entity and its lists, preserving its identity and version rather than constructing a replacement entity.

### 4. Recipe as the owner of two ordered value collections

**Choice:** Store recipe fields in `recipes`, ingredients in `recipe_ingredients`, and steps in `recipe_instructions`. Map the children as lazy `@ElementCollection` lists with an `@OrderColumn`.

```text
recipes
  id (PK)
  version, title, description, servings, vegetarian
      |
      +-- recipe_ingredients
      |     recipe_id (PK, FK), position (PK), text
      |
      +-- recipe_instructions
            recipe_id (PK, FK), position (PK), text
```

**Why:** An ingredient entry or instruction has no independent lifecycle or public identity in this assignment. Both belong to one recipe and preserve the author's order. Separate child tables support per-entry searching and explicit relational constraints.

**Tradeoff:** Full list replacement may generate multiple deletes, updates, or inserts. Bounded lists and JDBC batching limit the work, but generated SQL still needs inspection. Independently editable children with stable IDs would justify child entities later. JSON storage would make the current per-entry indexing and querying a different design problem.

### 5. Literal substring matching and aligned indexes

**Choice:** Normalize search terms with trimming and `Locale.ROOT` lowercasing, then use `lower(text) LIKE` with escaped pattern characters. The escape character `!` is itself escaped, followed by `%` and `_`.

**Why:** User input is text to find, not a SQL wildcard language. Criteria supplies values through the ORM query mechanism rather than concatenating user input into SQL. Repeated ingredient parameters preserve commas within a term as literal text. Blank terms are ignored.

GIN trigram indexes on `lower(text)` match the expression used by the query. Ordinary B-tree text indexes do not generally solve leading-wildcard substring searches.

**Tradeoff:** Trigram indexes consume storage and increase write work. Short terms, common terms, and exclusion-only searches may still scan many rows. Index presence is not proof of a fast plan, and there is no stemming, synonym expansion, fuzzy matching, or relevance ranking. Cross-language case behavior would deserve explicit tests if multilingual search became a product requirement.

### 6. Three-query DTO assembly instead of collection fetch joins

**Choice:** For a nonempty slice, select the root page, fetch all its ingredients in one query, and fetch all its instructions in another. Detail reads use the same assembly approach. An empty slice needs only the root query.

**Why:** Lazy loading both lists for 20 entities could cause 41 SELECTs. Joining both lists can multiply rows: 10 ingredients and 8 steps yield 80 rows for one recipe. Separate bulk reads avoid both problems and keep the intended SELECT count independent of page size.

Child queries load every child for the returned IDs, not just matching children. Assembly preserves the original root order because an IN clause alone does not guarantee ordering.

**Tradeoff:** This needs repository projection records (`RecipeRoot` and `RecipeChild`) and service assembly code. It is a deliberate performance reason for repository projections; public response DTOs are still assembled outside repositories. Three queries bound round trips, not scanned rows or total execution time. The query-count target applies to these reads, not all write operations.

### 7. Count-free slices and bounded offsets

**Choice:** Fetch `size + 1` root rows, use the extra row for `hasNext`, and load children only for the returned page. Return results, page, size, and hasNext without an exact total.

**Why:** A client can navigate forward without paying for a count query on every search. Limits are applied to root rows in SQL before children are loaded. ID-ascending ordering makes results deterministic within the query snapshot.

**Tradeoff:** The response cannot directly display an exact number of pages. Offset pagination becomes expensive at depth, so page size is 1–100 (default 20), and offset is capped at 10,000 by default. Keyset pagination is the deferred extension for deep browsing. Separate page requests are not one shared snapshot and can shift as data changes.

### 8. Service transactions and consistent reads

**Choice:** Writes are transactional. Multi-query read assembly uses short read-only repeatable-read transactions. Open Session in View is disabled, and Hibernate is configured to fail on collection-fetch pagination.

**Why:** A recipe's root fields and lists must be updated atomically. For reads, repeatable-read keeps all three queries on one database snapshot; read-committed could combine old root data with newly committed children. DTO construction completes before the transaction ends, and HTTP serialization happens afterward.

PUT does not need a second save call because the loaded entity is managed and Hibernate detects changes. DELETE explicitly flushes pending work, while final commit remains within the service transaction boundary.

**Tradeoff:** A snapshot holds database resources for its duration. Bounds and timeouts matter, and these transactions should never include remote calls or response transmission. Turning off OSIV also means newly added lazy-access code must stay inside an appropriate transaction.

### 9. Optimistic locking for overlapping writes

**Choice:** Put `@Version` on the recipe aggregate and use `OPTIMISTIC_FORCE_INCREMENT` when loading for replacement. Deletion participates in version checking.

**Why:** Concurrent writers should not silently overwrite one another. Forcing a parent version increment also covers child-only or identical replacements. On conflict, the transaction rolls back, including child changes, and the API returns 409.

**Tradeoff:** This protects overlapping server transactions. It does not detect an old form submitted after another write has already committed, because the request supplies no expected version. ETag/If-Match or an explicit client version would address that separate problem. Optimistic locking avoids deliberately locking reads, but writes still take normal database locks and may conflict.

### 10. Finite payload and query limits

**Choice:** Enforce these initial limits:

| Input | Limit | Reason |
| --- | --- | --- |
| Title | 200 characters | Bound a short identifying field |
| Description | 10,000 characters | Allow paragraphs while bounding payloads |
| Ingredient entry | 1,000 characters | Allow quantities and preparation notes |
| Instruction step | 2,000 characters | Allow detailed steps |
| Ingredients / instructions | 1–100 / 0–100 entries | Bound collection reads and replacement work |
| Search term | 200 characters | Bound each matching predicate |
| Include / exclude filters | 10 terms each before deduplication | Bound predicate count |
| Raw request body | 512 KiB | Bound buffering and JSON parsing input |
| Page size / offset | 100 / 10,000 maximum by default | Bound returned data and deep browsing |

**Why:** A page-size limit alone is insufficient if every recipe contains unlimited text and children. `RequestLimitsFilter` checks declared length and reads at most the body limit plus one byte, covering requests without Content-Length.

### 11. Admission control, timeouts, and batching

**Choice:** Use a non-waiting semaphore for recipe requests, a bounded Tomcat task queue, finite connection/thread settings, database deadlines, and JDBC batching.

**Why:** Unbounded waiting can turn a temporary overload into thread and memory exhaustion. The semaphore rejects detected saturation with 503 instead of building another application queue; permits are released in finally. Database deadlines limit slow work, while batching can reduce write round trips.

**Tradeoff:** There may be bounded waiting at different layers, and requests rejected at connector level may never receive an application-generated HTTP response. Production admission control should act before that boundary. The semaphore is per instance, not a global rate limiter; no 429 rate limiter is implemented. Pool budgets must account for all replicas and leave database capacity for operations. These numbers are configuration choices, not evidence of throughput capacity.

### 12. Tests that target risks at the appropriate boundary

**Choice:** Use focused unit tests, controller slices with a mocked service, and an explicit PostgreSQL Testcontainers integration profile.

**Why:** Fast tests cover normalization, service coordination, HTTP validation/statuses, literal comma binding, and admission/body limits. PostgreSQL tests exercise the actual migrations and provider behavior, which mocked repositories cannot establish. An in-memory substitute would not establish pg_trgm or PostgreSQL timeout behavior.

The integration suite checks 1/20/100-result query counts through HTTP serialization, complete ordered children, filtering/exclusion, atomic rollback, and overlapping write conflicts. It checks configured statement/lock timeout values and actively exercises statement cancellation.

**Tradeoff:** Docker is required for the integration profile. Current persistence checks are in a full Spring Boot integration suite, not a separate @DataJpaTest slice. A dedicated repository slice could help if query logic grows. Lock-contention timing, connector saturation, and production capacity still require additional targeted checks; configured values alone do not prove them.

## Verification

Run the database-independent unit and controller suites:

```powershell
.\mvnw.cmd verify
```

Run those suites plus PostgreSQL integration checks with a working Docker engine:

```powershell
.\mvnw.cmd verify -Ppostgres-it
```

On macOS/Linux use `./mvnw`. The integration profile starts its own disposable PostgreSQL 14.17 container and does not silently skip when Docker is missing.

For query-plan evaluation, run `psql -v ON_ERROR_STOP=1 -f scripts/explain-search.sql` against a disposable migrated database. Inspect actual rows, execution time, and buffers for selective and broad searches; the script rolls back its fixtures.

## CI/CD

[.github/workflows/ci-cd.yml](.github/workflows/ci-cd.yml) runs on every push/PR: `./mvnw verify` (unit/controller tests plus JaCoco coverage), the `postgres-it` Testcontainers profile, a SonarQube scan with an enforced quality gate, then — on `main` only, after the gate passes — an image build/push to ECR and an App Runner deployment. AWS is provisioned once by hand (no Terraform/CDK; this is a showcase pipeline, not production IaC) — see [DEPLOYMENT.md](docs/DEPLOYMENT.md) for the setup/teardown commands and the GitHub secrets/variables the workflow expects.
