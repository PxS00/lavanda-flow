FROM node:24-alpine AS frontend-build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN corepack enable \
    && corepack prepare pnpm@10.32.0 --activate \
    && pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

FROM eclipse-temurin:25-jdk AS backend-build

WORKDIR /workspace/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw dependency:go-offline
COPY backend/src src
COPY --from=frontend-build /workspace/frontend/dist/frontend/browser/ src/main/resources/static/
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=backend-build /workspace/backend/target/lavanda-flow-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
