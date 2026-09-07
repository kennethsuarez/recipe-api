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

### Search

- `vegetarian=true` restricts results; false or omission does not.
- `servings` is an exact positive integer match.
- Repeat `includeIngredient` for terms that must each match an ingredient.
- Repeat `excludeIngredient` for terms that must match no ingredient.
- `instruction` matches within any single step.
- `page` defaults to 0; `size` defaults to 20.


## Design and operations
