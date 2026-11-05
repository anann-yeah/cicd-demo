# ---------- Stage 1: build the jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Copy pom first so dependency download is cached unless pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests already ran in the pipeline's Maven stage, so skip them here
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:17-jre
# The pipeline passes the git commit SHA so the running app can show its version
ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}
# Run as a non-root user with a numeric UID (Kubernetes can verify numeric UIDs)
RUN addgroup --system --gid 10001 app && adduser --system --uid 10001 --ingroup app app
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
