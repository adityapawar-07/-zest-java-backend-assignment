# Product API

Spring Boot REST API for CRUD operations on Products (and their Items),
with JWT authentication, role-based access control, and PostgreSQL/H2
support.

## Stack

- Java 17, Spring Boot 3.3.4
- Spring Data JPA (Hibernate)
- Spring Security + JWT (jjwt)
- H2 (local/dev) / PostgreSQL 16 (Docker)
- springdoc-openapi (Swagger)
- JUnit 5 + Mockito
- Docker & Docker Compose

## Running it

**Locally (H2):**
```bash
mvn spring-boot:run
```
Or import into Eclipse as a Maven project and run `ProductApiApplication`.

**With Docker (Postgres):**
```bash
docker compose up --build
```

App runs on `http://localhost:8080` either way.

## Architecture

```
controller/   REST endpoints
service/      Business logic
repository/   Spring Data JPA repositories
model/        JPA entities (Product, Item, AppUser, RefreshToken)
dto/          Request/response objects
exception/    Global exception handling
security/     JWT filter, token service, refresh rotation
config/       Security, CORS, async, OpenAPI, data seeding
```

## Auth

Seeded accounts on first run:

| Username | Password | Role       |
|----------|----------|------------|
| admin    | admin123 | ROLE_ADMIN |
| user     | user123  | ROLE_USER  |

```
POST /api/v1/auth/login
{ "username": "admin", "password": "admin123" }
```
Returns `accessToken` + `refreshToken`. Send the access token as
`Authorization: Bearer <token>` on protected routes.

- Reads (`GET`) — any authenticated user
- Writes (`POST`/`PUT`/`DELETE`) — ADMIN only
- Refresh tokens rotate on use (`/api/v1/auth/refresh`)

## Endpoints

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout

GET    /api/v1/products
GET    /api/v1/products/{id}
POST   /api/v1/products
PUT    /api/v1/products/{id}
DELETE /api/v1/products/{id}
GET    /api/v1/products/{id}/items
POST   /api/v1/products/{id}/items
```

## Docs

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Tests

```bash
mvn test
```
Unit tests (Mockito) for service/security layers, plus integration tests
running through the real security stack against H2.
