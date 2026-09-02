# Product API

A Spring Boot REST API for full CRUD operations on `Product` (with a related
`Item` sub-resource), built against the Zest India Java Backend Developer
assignment spec. Covers CRUD, JWT auth with refresh-token rotation,
role-based authorization, DB indexing, async processing, CORS, Swagger/
OpenAPI docs, a full JUnit 5 + Mockito test suite, and Docker/Docker Compose.

## Stack

- Java 17
- Spring Boot 3.3.4
- Spring Data JPA (Hibernate)
- Spring Security + JWT (jjwt 0.12.x)
- H2 in-memory database for local dev and tests (zero setup) / PostgreSQL 16 in Docker
- Jakarta Bean Validation
- springdoc-openapi (Swagger UI)
- JUnit 5, Mockito, Spring Boot Test
- Maven
- Docker & Docker Compose

## Run it locally (H2, zero setup)

### Option A: Eclipse

1. Unzip the project.
2. `File > Import > Maven > Existing Maven Projects`, select the
   `product-api` folder, click Finish.
3. Let Eclipse download dependencies (first run may take a minute).
4. Run `ProductApiApplication.java` as a **Java Application**.
5. The API starts on `http://localhost:8080`.

### Option B: command line

```bash
mvn spring-boot:run
```

## Run it with Docker

No local Java/Maven/PostgreSQL install needed — this builds the app and
starts it alongside a PostgreSQL 16 database.

```bash
docker compose up --build
```

- API: `http://localhost:8080`
- PostgreSQL: exposed on host port `5432` if you want to connect with a
  client (psql, DBeaver, etc.) — database `productdb`, user/password
  `productapi`/`productapi`.
- Data persists in the `postgres_data` Docker volume across restarts
  (unlike the H2 profile, which resets on every app restart).
- Stop with `docker compose down` (add `-v` to also drop the Postgres volume).

The `docker` Spring profile (`application-docker.properties`) drives this —
datasource, JWT secret, and CORS origins are all overridable via environment
variables set in `docker-compose.yml`. **Change `APP_JWT_SECRET` to a real
secret before deploying this anywhere that isn't your own machine.**

To build just the image without Compose:

```bash
docker build -t product-api .
docker run -p 8080:8080 product-api
```
(this uses the default H2 profile unless you set `SPRING_PROFILES_ACTIVE=docker`
and the Postgres env vars yourself.)

## API Documentation (Swagger / OpenAPI)

With the app running:

- Interactive UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Click **Authorize** in the UI and paste an `accessToken` (from
`POST /api/v1/auth/login`, no `Bearer ` prefix needed) to try protected
endpoints directly from the browser.

**Swagger UI tip:** only expand ("Try it out") the one endpoint panel
you're currently testing. If a previous panel (e.g. login) is still
expanded when you click Execute on a different one, Swagger can submit
the wrong panel's request — always check the `curl`/Request URL shown
after Execute matches the endpoint you meant to call.

## Database

**Local/Eclipse (default):** in-memory H2, zero setup. Data resets every
restart. Console: `http://localhost:8080/h2-console` (JDBC URL
`jdbc:h2:mem:productdb`, user `sa`, blank password).

**Docker (`docker` profile):** PostgreSQL 16, data persisted in a volume.

Either way, the schema (`product`, `item`, plus `app_user` /
`refresh_token` for auth) is auto-created from the JPA entities on startup
(`spring.jpa.hibernate.ddl-auto=update`), matching the structure in the
assignment doc.

## Architecture

```
controller/   REST endpoints (ProductController, ItemController, AuthController)
service/      Business logic, interface + impl
repository/   Spring Data JPA repositories
model/        JPA entities (Product, Item, AppUser, RefreshToken)
dto/          Request/response DTOs (keeps entities out of the API contract)
exception/    Global exception handler + standardized ErrorResponse
security/     JWT issuing/validation, refresh-token rotation, auth filter
config/       Security, CORS, async executor, OpenAPI, dev data seeding
```

Requests are validated with Jakarta Validation annotations on the DTOs.
All errors (404s, validation failures, auth failures, unexpected
exceptions) are funneled through `GlobalExceptionHandler` /
`RestAuthenticationHandlers` into one consistent JSON error shape:

```json
{
  "timestamp": "2026-09-02T10:15:30",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found with id: 999",
  "path": "/api/v1/products/999"
}
```

## Authentication

Every `/api/v1/products/**` endpoint requires a JWT access token in the
`Authorization: Bearer <token>` header. `/api/v1/auth/**` and the Swagger
docs are open.

Two accounts are auto-seeded on first startup (only if the user table is
empty) so you can test immediately without registering:

| Username | Password | Role       |
|----------|----------|------------|
| admin    | admin123 | ROLE_ADMIN |
| user     | user123  | ROLE_USER  |

| Method | URL                      | Description                                  |
|--------|---------------------------|-----------------------------------------------|
| POST   | `/api/v1/auth/register`   | Create an account (`role` optional: USER/ADMIN, defaults to USER) |
| POST   | `/api/v1/auth/login`      | Log in, returns `accessToken` + `refreshToken` |
| POST   | `/api/v1/auth/refresh`    | Exchange a refresh token for a new pair (old one is revoked — rotation) |
| POST   | `/api/v1/auth/logout`     | Revoke a refresh token                        |

```
POST /api/v1/auth/login
Content-Type: application/json

{ "username": "admin", "password": "admin123" }
```
Response:
```json
{ "accessToken": "eyJhbGciOi...", "refreshToken": "q1w2e3...", "tokenType": "Bearer" }
```
Use the `accessToken` in Postman: **Authorization tab → Bearer Token → paste it.**
Access tokens expire in 15 minutes (`app.jwt.access-token-expiry-ms`); when
that happens, call `/api/v1/auth/refresh` with the `refreshToken` to get a
new pair — the old refresh token is revoked the moment it's used (rotation),
so it can't be replayed.

## Role-based authorization

- **ROLE_USER and ROLE_ADMIN**: can call all `GET` endpoints (read-only).
- **ROLE_ADMIN only**: `POST`, `PUT`, `DELETE` on products and items.

Calling a write endpoint as `user`/`user123` returns a `403` with the
standard error JSON shape. Enforced via `@PreAuthorize("hasRole('ADMIN')")`
on the controller methods.

## CORS

Configured in `SecurityConfig` / `application.properties`
(`app.cors.allowed-origins`) — defaults to `http://localhost:3000` and
`http://localhost:5173` for local frontend dev. Add your own origin there
if you're calling this from a browser app.

## HTTPS enforcement

Off by default (`app.security.require-https=false`) so local Postman/http
testing keeps working. Flip it to `true` once this sits behind a real TLS
terminator (reverse proxy, load balancer, or `server.ssl.*` configured
directly) — Spring Security will then reject plain HTTP requests.

## Database indexing

- `product.product_name` — indexed for name-based lookups/filtering.
- `item.product_id` — indexed (this is the FK used by the items-by-product query).
- `app_user.username` — unique indexed (login lookups).
- `refresh_token.token` — unique indexed (refresh/rotation lookups).

## Async processing

`NotificationService` methods are annotated `@Async` and run on a
dedicated thread pool (`AsyncConfig`) instead of the request thread —
`ProductServiceImpl` fires a "product created"/"product deleted"
notification this way after each operation, so the HTTP response doesn't
wait on it. Watch the console log for `[async] ... (thread=async-task-N)`.

## Testing

```bash
mvn test
```

- **Unit tests** (Mockito, no Spring context, no DB):
  - `ProductServiceImplTest` — CRUD logic, not-found handling, item listing.
  - `AuthServiceTest` — register (default/explicit role, duplicate username),
    login (success/bad credentials), refresh, logout.
  - `RefreshTokenServiceTest` — token creation, rotation, replay protection,
    expiry handling, revocation.
- **Integration tests** (`@SpringBootTest` + MockMvc, real Spring context,
  real security filter chain, H2 in-memory DB, wrapped in a rolled-back
  `@Transactional` per test method):
  - `ProductControllerIntegrationTest` — full CRUD flow end-to-end through
    HTTP, role enforcement (admin vs. user), validation errors, pagination,
    404s, unauthenticated/invalid-token handling, item add/list.
  - `AuthControllerIntegrationTest` — register, login, refresh-token
    rotation and replay rejection, logout, and their failure paths.

Tests run against a dedicated H2 instance (`src/test/resources/application.properties`,
`create-drop` schema) so they never touch your local dev data.

### Sample create request

```
POST /api/v1/products
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "productName": "Wireless Mouse",
  "createdBy": "admin"
}
```

### Sample add-item request

```
POST /api/v1/products/1/items
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "quantity": 25
}
```

## Endpoint summary

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout

GET    /api/v1/products              (paginated)
GET    /api/v1/products/{id}
POST   /api/v1/products              (ADMIN)
PUT    /api/v1/products/{id}         (ADMIN)
DELETE /api/v1/products/{id}         (ADMIN)
GET    /api/v1/products/{id}/items
POST   /api/v1/products/{id}/items   (ADMIN)
```

## Submission checklist (per the assignment doc)

- [ ] Push this repository to a **new PUBLIC** GitHub repo on your account.
- [ ] Confirm `Dockerfile` and `docker-compose.yml` are included (they are).
- [ ] Confirm this `README.md` has setup instructions + architecture (it does).
- [ ] Do **not** submit a ZIP — only the GitHub repo URL goes in the Google Form.
- [ ] Submit the Google Form: full name, email, mobile number, years of
      experience, GitHub repo URL, time taken.
