# Local Development Environment

## PostgreSQL

The local PostgreSQL instance is provided by the repository-level `compose.yaml`.

### Default Configuration

- **Service:** `postgres`
- **Database:** `lavanda_flow`
- **User:** `lavanda`
- **Password:** `lavanda`
- **Host Port:** `5432`

### Overriding Defaults

Configuration values can be overridden through environment variables without modifying or committing secrets:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_PORT`

#### Examples

Custom port:

```bash
POSTGRES_PORT=5433 docker compose up -d
```

Custom credentials:

```bash
POSTGRES_USER=myuser POSTGRES_PASSWORD=mypassword docker compose up -d
```

### Managing Infrastructure

Start services in detached mode:

```bash
docker compose up -d
```

Check status and healthcheck:

```bash
docker compose ps
```

Stop services:

```bash
docker compose down
```

### Data Persistence

The named volume `lavanda-postgres-data` persists database data across container restarts.

To reset the database data completely:

```bash
docker compose down -v
```

### Backend Integration

The `local` Spring profile is opt-in. Start the backend locally with one of the following:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

or:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

The backend is configured with `spring.docker.compose.lifecycle-management: start-only` under the `local` profile. When that profile is selected, Spring Boot Docker Compose support can automatically start the container if it is not already running, without tearing down the database container when the JVM exits.

Without an active profile, the application uses the environment-neutral base configuration and does not implicitly enable repository-local Docker Compose integration.

> [!NOTE]
> Spring Boot Docker Compose support is intended strictly for local development. Production environments must provide standard PostgreSQL connection properties (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) and must not depend on Spring Boot managing Docker Compose.
