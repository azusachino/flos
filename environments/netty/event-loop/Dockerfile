# syntax=docker/dockerfile:1

# Build stage: the full reactor is needed because Maven resolves the module tree from the root
# pom.xml before restricting the actual build to one module with -pl/-am.
FROM docker.io/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY modules ./modules
RUN mvn -pl modules/netty/event-loop-lab -am -DskipTests package

# Runtime stage: Amazon Corretto, the same JDK distribution mise.toml pins for local development
# (java = "corretto-17"), so the container runs the identical vendor build a contributor tests
# against locally. Corretto does not publish a separate JRE-only image, unlike Eclipse Temurin,
# but its Alpine tag is genuinely multi-arch (amd64 and arm64) where Temurin's Alpine JRE tag is
# amd64-only -- smaller than the Debian-based fallback and still runs on Apple Silicon natively.
FROM docker.io/library/amazoncorretto:17-alpine
RUN addgroup -S flos && adduser -S flos -G flos
USER flos
WORKDIR /app
COPY --from=build --chown=flos:flos /workspace/modules/netty/event-loop-lab/target/event-loop-lab.jar app.jar
EXPOSE 9000
HEALTHCHECK --interval=5s --timeout=3s --retries=12 \
    CMD nc -z localhost 9000 || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
